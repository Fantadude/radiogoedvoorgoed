import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const PODCAST_RSS_URL = "https://radiogoedvoorgoed.nl/feed/podcast/";

interface PodcastEpisode {
  id: string;
  title: string;
  description: string;
  audioUrl: string;
  link: string;
  pubDate: string;
  author: string;
}

function extractCDATA(text: string): string {
  // Handle CDATA content and HTML comments from the feed
  return text
    .replace(/<!--\[CDATA\[(.*?)\]\]-->/gs, '$1')
    .replace(/<!\[CDATA\[(.*?)\]\]>/gs, '$1')
    .replace(/&amp;/g, '&')
    .replace(/&#8220;/g, '"')
    .replace(/&#8221;/g, '"')
    .replace(/&#8230;/g, '...')
    .replace(/&#\d+;/g, '')
    .trim();
}

function parseRSS(xmlText: string): PodcastEpisode[] {
  const episodes: PodcastEpisode[] = [];
  
  // Simple regex-based parsing for podcast RSS
  const itemRegex = /<item>([\s\S]*?)<\/item>/g;
  let match;
  
  while ((match = itemRegex.exec(xmlText)) !== null) {
    const itemContent = match[1];
    
    // Extract fields
    const titleMatch = /<title>([\s\S]*?)<\/title>/i.exec(itemContent);
    const linkMatch = /<link>([\s\S]*?)(?:<\/link>|[\r\n])/i.exec(itemContent);
    const pubDateMatch = /<pubdate>([\s\S]*?)<\/pubdate>/i.exec(itemContent);
    const descriptionMatch = /<description>([\s\S]*?)<\/description>/i.exec(itemContent);
    const guidMatch = /<guid[^>]*>([\s\S]*?)<\/guid>/i.exec(itemContent);
    const enclosureMatch = /<enclosure[^>]*url="([^"]*)"[^>]*>/i.exec(itemContent);
    const authorMatch = /<dc:creator>([\s\S]*?)<\/dc:creator>/i.exec(itemContent) || 
                        /<itunes:author>([\s\S]*?)<\/itunes:author>/i.exec(itemContent);
    
    if (titleMatch && enclosureMatch) {
      const episode: PodcastEpisode = {
        id: guidMatch ? extractCDATA(guidMatch[1]) : enclosureMatch[1],
        title: extractCDATA(titleMatch[1]),
        description: descriptionMatch ? extractCDATA(descriptionMatch[1]) : '',
        audioUrl: enclosureMatch[1],
        link: linkMatch ? extractCDATA(linkMatch[1]) : '',
        pubDate: pubDateMatch ? pubDateMatch[1] : '',
        author: authorMatch ? extractCDATA(authorMatch[1]) : 'Robert & Joost',
      };
      
      episodes.push(episode);
    }
  }
  
  return episodes;
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    console.log('Fetching podcast RSS feed...');
    
    const response = await fetch(PODCAST_RSS_URL, {
      headers: {
        'User-Agent': 'GoedvoorGoed-Radio-App/1.0',
        'Accept': 'application/rss+xml, application/xml, text/xml',
      },
    });
    
    if (!response.ok) {
      throw new Error(`Failed to fetch RSS feed: ${response.status}`);
    }
    
    const xmlText = await response.text();
    console.log(`Fetched RSS feed, ${xmlText.length} bytes`);
    
    const episodes = parseRSS(xmlText);
    console.log(`Parsed ${episodes.length} podcast episodes`);
    
    return new Response(JSON.stringify({ 
      episodes,
      fetchedAt: new Date().toISOString(),
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
    
  } catch (error) {
    console.error('Error fetching podcasts:', error);
    
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    return new Response(JSON.stringify({ error: errorMessage }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
