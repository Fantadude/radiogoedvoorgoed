import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { Client } from "https://deno.land/x/mysql@v2.12.1/mod.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Database configuration
const DB_CONFIG = {
  hostname: "86.84.18.58",
  port: 3306,
  username: "request",
  password: "user",
  db: "radiodj2006",
};

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const action = url.searchParams.get('action');

  let client: Client | null = null;

  try {
    console.log(`RadioDJ function called with action: ${action}`);
    
    client = await new Client().connect(DB_CONFIG);
    console.log('Connected to MySQL database');

    if (action === 'songs') {
      // Get songs from the songs table
      const limit = parseInt(url.searchParams.get('limit') || '100');
      const search = url.searchParams.get('search') || '';
      
      let query = `
        SELECT ID, artist, title, album, duration, genre 
        FROM songs 
        WHERE enabled = 1
      `;
      
      const params: any[] = [];
      
      if (search) {
        query += ` AND (artist LIKE ? OR title LIKE ?)`;
        params.push(`%${search}%`, `%${search}%`);
      }
      
      query += ` ORDER BY artist, title LIMIT ?`;
      params.push(limit);
      
      console.log(`Executing query: ${query}`);
      const songs = await client.query(query, params);
      
      console.log(`Found ${songs.length} songs`);
      
      await client.close();
      
      return new Response(JSON.stringify({ songs }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
    
    if (action === 'request' && req.method === 'POST') {
      const body = await req.json();
      const { songId, name, message } = body;
      
      if (!songId) {
        throw new Error('Song ID is required');
      }
      
      console.log(`Creating request for song ${songId} from ${name}`);
      
      // Insert into requests table (RadioDJ's standard requests table)
      const result = await client.execute(
        `INSERT INTO requests (songID, name, msg, requested) VALUES (?, ?, ?, NOW())`,
        [songId, name || 'App User', message || '']
      );
      
      console.log('Request inserted successfully');
      
      await client.close();
      
      return new Response(JSON.stringify({ success: true, message: 'Request submitted!' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
    
    if (action === 'nowplaying') {
      // Get the currently playing song from history
      const result = await client.query(`
        SELECT h.date_played, s.artist, s.title, s.album, s.duration
        FROM history h
        JOIN songs s ON h.songID = s.ID
        ORDER BY h.date_played DESC
        LIMIT 1
      `);
      
      await client.close();
      
      if (result.length > 0) {
        return new Response(JSON.stringify({ nowPlaying: result[0] }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }
      
      return new Response(JSON.stringify({ nowPlaying: null }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    await client.close();
    
    return new Response(JSON.stringify({ error: 'Invalid action' }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
    
  } catch (error) {
    console.error('Error in radiodj function:', error);
    
    if (client) {
      try {
        await client.close();
      } catch (e) {
        console.error('Error closing client:', e);
      }
    }
    
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    return new Response(JSON.stringify({ error: errorMessage }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
