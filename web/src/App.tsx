import { useState } from 'react'
import RadioPlayer from './components/RadioPlayer'
import Requests from './components/Requests'
import Podcasts from './components/Podcasts'
import './App.css'

type Tab = 'radio' | 'requests' | 'podcasts'

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('radio')

  return (
    <div className="app">
      <header className="app-header">
        <h1>radiogoedvoorgoed</h1>
        <nav className="tab-nav">
          <button
            className={activeTab === 'radio' ? 'active' : ''}
            onClick={() => setActiveTab('radio')}
          >
            Radio
          </button>
          <button
            className={activeTab === 'requests' ? 'active' : ''}
            onClick={() => setActiveTab('requests')}
          >
            Requests
          </button>
          <button
            className={activeTab === 'podcasts' ? 'active' : ''}
            onClick={() => setActiveTab('podcasts')}
          >
            Podcasts
          </button>
        </nav>
      </header>

      <main className="app-main">
        {activeTab === 'radio' && <RadioPlayer />}
        {activeTab === 'requests' && <Requests />}
        {activeTab === 'podcasts' && <Podcasts />}
      </main>
    </div>
  )
}

export default App
