import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import 'primeicons/primeicons.css'
import App from './App.vue'
import router from './router'
import './style.css'

// Aura sin override usa el azul default de PrimeVue — identico al de
// cualquier boilerplate. Paleta propia (emerald) para que el admin tenga
// identidad de marca, no la de la libreria.
const AdminPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#ecfdf5',
      100: '#d1fae5',
      200: '#a7f3d0',
      300: '#6ee7b7',
      400: '#34d399',
      500: '#10b981',
      600: '#059669',
      700: '#047857',
      800: '#065f46',
      900: '#064e3b',
      950: '#022c22',
    },
  },
})

createApp(App)
  .use(router)
  .use(PrimeVue, { theme: { preset: AdminPreset } })
  .mount('#app')
