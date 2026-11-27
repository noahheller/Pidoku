import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { warmUpApi } from './api/client'
import { applyCosmetics } from './components/cosmetics'

warmUpApi()
// The player's theme and digits before anything renders, so the page doesn't flash the default.
applyCosmetics()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
