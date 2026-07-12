<script setup lang="ts">
// Ayuda / FAQ del sistema — explicación sencilla del flujo completo para el
// usuario final (admin del transporte clínico). No consume API; es contenido
// estático organizado por secciones colapsables.
import { ref } from 'vue'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import Tag from 'primevue/tag'

const openPanels = ref<number[]>([0])

interface FaqItem {
  q: string
  a: string
}

interface FaqSection {
  icon: string
  title: string
  desc: string
  items: FaqItem[]
}

const sections: FaqSection[] = [
  {
    icon: 'pi pi-info-circle',
    title: '¿Qué es este sistema?',
    desc: 'Una explicación general del panel administrativo.',
    items: [
      {
        q: '¿Para qué sirve este panel?',
        a: 'Este panel te permite administrar el transporte del personal de la clínica: dar de alta paradas, vehículos y conductores, diseñar rutas con sus horarios, configurar calendarios de servicio y generar los viajes que después usan los pasajeros para reservar sus asientos.',
      },
      {
        q: '¿Quiénes usan el sistema?',
        a: 'Tres tipos de usuarios: el ADMIN (vos, que configuras todo desde este panel), el DRIVER (el conductor, que marca llegadas a paradas y reporta incidencias desde la app móvil) y el WORKER (el trabajador, que busca viajes y reserva asientos desde la app).',
      },
      {
        q: '¿Por dónde empiezo?',
        a: 'El flujo recomendado es: 1) Cargar paradas, vehículos y usuarios. 2) Crear rutas y ordenar sus paradas. 3) Definir calendarios de servicio. 4) Configurar perfiles de tiempo y tiempos por tramo. 5) Crear plantillas de viaje. 6) Generar viajes. 7) Monitorear operación diaria y reportes.',
      },
    ],
  },
  {
    icon: 'pi pi-map-marker',
    title: 'Paradas',
    desc: 'Los puntos físicos donde sube y baja el personal.',
    items: [
      {
        q: '¿Qué es una parada?',
        a: 'Es un punto físico en la ruta donde el vehículo se detiene. Puede ser SEDE (la clínica) o PARADERO (un punto de recolección en la calle). Cada parada tiene un código único, un nombre y opcionalmente coordenadas GPS.',
      },
      {
        q: '¿Puedo dar de baja una parada?',
        a: 'Sí, desactivándola con el botón de desactivar. La parada deja de usarse en nuevas rutas, pero no se borra para no perder el historial de viajes que la usaron.',
      },
    ],
  },
  {
    icon: 'pi pi-car',
    title: 'Vehículos y asientos',
    desc: 'Los vehículos y su inventario de asientos.',
    items: [
      {
        q: '¿Qué es un vehículo?',
        a: 'Es el transporte físico (bus, combi, auto). Tiene un código interno, una patente y una cantidad de asientos. Cada vehículo tiene un inventario individual de asientos que se gestiona por separado en la sección "Asientos".',
      },
      {
        q: '¿Qué son los asientos?',
        a: 'Cada butaca individual del vehículo. Se configuran con un número, una etiqueta (ej. "1A") y un estado: ACTIVO (se puede reservar), BLOQUEADO (fuera de servicio por mantenimiento) o RETIRADO (ya no existe físicamente).',
      },
    ],
  },
  {
    icon: 'pi pi-users',
    title: 'Usuarios',
    desc: 'Las personas que usan el sistema.',
    items: [
      {
        q: '¿Qué roles hay?',
        a: 'ADMIN: accede a este panel y configura todo. DRIVER: conduce los viajes y marca llegadas desde la app. WORKER: busca viajes y reserva asientos desde la app. Solo el rol ADMIN puede acceder a la administración.',
      },
      {
        q: '¿Qué pasa si desactivo un usuario?',
        a: 'No puede iniciar sesión ni usar la app. Pero sus viajes e historial se conservan. Si el usuario era conductor de una plantilla, deberás reasignar otro conductor.',
      },
    ],
  },
  {
    icon: 'pi pi-directions',
    title: 'Rutas, segmentos y paradas de ruta',
    desc: 'El diseño del recorrido.',
    items: [
      {
        q: '¿Qué es una ruta?',
        a: 'Es el recorrido que hace un vehículo. Tiene un sentido: IDA ( hacia la clínica) o VUELTA (de regreso). Una ruta puede emparejarse con su contraria para vincular el viaje de ida con el de vuelta.',
      },
      {
        q: '¿Qué son las paradas de ruta?',
        a: 'Es el orden en que el vehículo visita las paradas. Por ejemplo: parada 1 (Sede), parada 2 (Paradero Norte), parada 3 (Paradero Sur). Cada parada tiene un tiempo de espera (dwell) y permisos para subir/bajar pasajeros.',
      },
      {
        q: '¿Y los segmentos?',
        a: 'Un segmento es el tramo entre dos paradas consecutivas. Si la ruta tiene 5 paradas, hay 4 segmentos. Los segmentos sirven para definir cuántos minutos tarda el vehículo en ir de una parada a la siguiente.',
      },
    ],
  },
  {
    icon: 'pi pi-calendar',
    title: 'Calendarios y excepciones',
    desc: 'Cuándo opera el servicio.',
    items: [
      {
        q: '¿Qué es un calendario?',
        a: 'Define qué días opera una plantilla de viaje. Tiene una fecha de vigencia (desde/hasta) y marca qué días de la semana están activos (lunes a viernes, por ejemplo). Una plantilla solo genera viajes si su calendario está activo y el día corresponde.',
      },
      {
        q: '¿Qué es una excepción de calendario?',
        a: 'Es una fecha específica que se agrega o se saca del servicio. Por ejemplo: el calendario opera de lunes a viernes, pero un feriado cae martes — agregás una excepción REMOVE para ese martes y no se generan viajes ese día. O al revés: si el calendario no incluye sábados pero un sábado específico hay servicio, agregás una excepción ADD.',
      },
    ],
  },
  {
    icon: 'pi pi-clock',
    title: 'Perfiles de tiempo y tiempos de segmento',
    desc: 'Cuánto tarda cada tramo según condiciones.',
    items: [
      {
        q: '¿Qué es un perfil de tiempo?',
        a: 'Define condiciones horarias que afectan los tiempos de viaje. Por ejemplo: "Horario pico matutino" (7:00 a 9:00, lunes a viernes) con prioridad alta. El sistema compara fecha, hora y día de la semana para elegir qué perfil aplicar a cada tramo del viaje.',
      },
      {
        q: '¿Qué son los tiempos de segmento?',
        a: 'Es la matriz que dice cuántos minutos tarda el vehículo en recorrer cada segmento bajo cada perfil. Por ejemplo: el segmento "Parada 1 → Parada 2" tarda 10 minutos en horario normal, pero 18 minutos en hora pico. El sistema usa esta matriz para calcular la hora estimada de llegada a cada parada.',
      },
      {
        q: '¿Cómo elige el sistema qué perfil aplicar?',
        a: 'Por prioridad (número más alto = más优先). Si varios perfiles coinciden con el día/hora, gana el de prioridad más alta. Si ninguno coincide, usa el perfil marcado como "por defecto".',
      },
    ],
  },
  {
    icon: 'pi pi-file',
    title: 'Plantillas de viaje',
    desc: 'Las reglas que generan los viajes.',
    items: [
      {
        q: '¿Qué es una plantilla?',
        a: 'Es la receta que combina todo: una ruta, un calendario, un vehículo, un conductor, una hora de salida y parámetros de reserva. El motor de generación lee las plantillas activas y crea los viajes concretos para cada fecha.',
      },
      {
        q: '¿Qué es el modo de referencia de perfil?',
        a: 'Define desde qué momento se calcula el tiempo de viaje: "Salida del viaje" (TRIP_DEPARTURE) usa la hora de salida del viaje para elegir el perfil. "Salida del segmento" (SEGMENT_DEPARTURE) usa la hora de llegada a cada parada, lo que es más preciso si el viaje cruza varias ventanas horarias.',
      },
      {
        q: '¿Qué son la apertura y cierre de reserva?',
        a: 'Apertura: cuántos días antes del servicio pueden empezar a reservar los pasajeros. Cierre: cuántos minutos antes de la salida deja de aceptarse reservas. Por ejemplo: 14 días antes y 30 minutos antes.',
      },
    ],
  },
  {
    icon: 'pi pi-sync',
    title: 'Corridas de generación',
    desc: 'El motor que materializa los viajes.',
    items: [
      {
        q: '¿Qué es una corrida de generación?',
        a: 'Es el proceso automático que lee las plantillas activas y crea los viajes para una ventana de fechas futuras. Cada corrida registra cuántos viajes generó, saltó (ya existían) o falló, con su estado (RUNNING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED).',
      },
      {
        q: '¿Se genera de forma automática?',
        a: 'Sí, hay un job automático que corre periódicamente. También podés disparar una generación manual desde "Operaciones" eligiendo una plantilla y una fecha específica.',
      },
    ],
  },
  {
    icon: 'pi pi-send',
    title: 'Viajes',
    desc: 'Las instancias concretas que usan los pasajeros.',
    items: [
      {
        q: '¿Qué es un viaje?',
        a: 'Es una instancia concreta de una plantilla en una fecha específica. Tiene un código único, vehículo asignado, conductor, hora de salida y llegada, y un estado: DRAFT (recién creado), PUBLISHED (visible para reservas), BOARDING (pasajeros subiendo), IN_PROGRESS (en marcha), COMPLETED (finalizado) o CANCELLED.',
      },
      {
        q: '¿Cómo cambio el estado de un viaje?',
        a: 'Desde "Viajes" pulsás el botón de editar y elegís el nuevo estado, o desde "Operaciones" seleccionás el viaje y el estado. El cambio es inmediato.',
      },
      {
        q: '¿Qué pasa si cancelo un viaje?',
        a: 'El viaje pasa a estado CANCELLED. Los pasajeros que ya reservaron verán que el viaje fue cancelado. Podés revertirlo cambiando el estado de vuelta.',
      },
    ],
  },
  {
    icon: 'pi pi-cog',
    title: 'Operaciones',
    desc: 'Acciones inmediatas sobre viajes.',
    items: [
      {
        q: '¿Qué puedo hacer desde Operaciones?',
        a: 'Dos cosas: cambiar el estado de un viaje existente (ej. pasar de PUBLISHED a IN_PROGRESS) y generar un viaje manualmente para una fecha específica eligiendo una plantilla.',
      },
      {
        q: '¿La generación manual es diferente a la automática?',
        a: 'En el resultado no: ambas crean un viaje con el mismo proceso. La diferencia es que la manual se hace para una sola fecha y plantilla, mientras que la automática (corrida) genera un lote para varias plantillas y fechas.',
      },
    ],
  },
  {
    icon: 'pi pi-exclamation-triangle',
    title: 'Incidencias',
    desc: 'Problemas reportados durante los viajes.',
    items: [
      {
        q: '¿Qué es una incidencia?',
        a: 'Es un problema reportado por el conductor durante un viaje: avería, retraso, accidente, etc. El conductor la crea desde la app móvil; vos podés verla, cambiarle el estado (OPEN, IN_REVIEW, RESOLVED) y agregar notas de resolución.',
      },
    ],
  },
  {
    icon: 'pi pi-chart-bar',
    title: 'Reportes',
    desc: 'Vistas para detectar problemas.',
    items: [
      {
        q: '¿Qué reportes hay?',
        a: 'Tres: Conflictos de horario (detecta vehículos con viajes superpuestos), Matriz de tiempos por tramo (muestra cuántos minutos tarda cada segmento según el perfil) y Disponibilidad de asientos (muestra qué asientos están ocupados/libres en cada tramo de un viaje específico).',
      },
      {
        q: '¿Para qué sirve el reporte de conflictos?',
        a: 'Para detectar si un vehículo o conductor tiene dos viajes que se superponen en el horario. Si hay conflictos, te indica los viajes involucrados para que reasignes.',
      },
    ],
  },
]
</script>

<template>
  <section class="help-view">
    <header class="help-header">
      <div>
        <h1>Ayuda — ¿Cómo funciona el sistema?</h1>
        <p class="help-subtitle">
          Recorré las secciones para entender cada parte del panel. Si recién empezás,
          te recomendamos leer de arriba hacia abajo.
        </p>
      </div>
    </header>

    <div class="help-flow">
      <div class="flow-step">
        <i class="pi pi-map-marker" aria-hidden="true"></i>
        <span>Paradas</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-car" aria-hidden="true"></i>
        <span>Vehículos</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-directions" aria-hidden="true"></i>
        <span>Rutas</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-calendar" aria-hidden="true"></i>
        <span>Calendarios</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-clock" aria-hidden="true"></i>
        <span>Tiempos</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-file" aria-hidden="true"></i>
        <span>Plantillas</span>
      </div>
      <i class="pi pi-arrow-right flow-arrow" aria-hidden="true"></i>
      <div class="flow-step">
        <i class="pi pi-send" aria-hidden="true"></i>
        <span>Viajes</span>
      </div>
    </div>

    <Accordion v-model:value="openPanels" multiple>
      <AccordionPanel
        v-for="(section, idx) in sections"
        :key="idx"
        :value="idx"
      >
        <AccordionHeader>
          <span class="help-section-header">
            <i :class="section.icon" aria-hidden="true"></i>
            <span>{{ section.title }}</span>
            <Tag :value="String(section.items.length)" severity="secondary" />
          </span>
        </AccordionHeader>
        <AccordionContent>
          <p class="help-section-desc">{{ section.desc }}</p>
          <dl class="faq-list">
            <div v-for="(item, itemIdx) in section.items" :key="itemIdx" class="faq-item">
              <dt>
                <i class="pi pi-question-circle" aria-hidden="true"></i>
                {{ item.q }}
              </dt>
              <dd>{{ item.a }}</dd>
            </div>
          </dl>
        </AccordionContent>
      </AccordionPanel>
    </Accordion>
  </section>
</template>

<style scoped>
.help-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.help-header h1 {
  margin: 0 0 0.25rem;
}
.help-subtitle {
  margin: 0;
  color: #52525b;
  font-size: 0.9375rem;
  max-width: 40rem;
}

.help-flow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 1rem 1.25rem;
  border: 1px solid rgba(0, 0, 0, 0.09);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
}
.flow-step {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(0, 0, 0, 0.06);
  font-weight: 600;
  font-size: 0.875rem;
  white-space: nowrap;
}
.flow-step i {
  color: #059669;
}
.flow-arrow {
  color: #a1a1aa;
  font-size: 0.75rem;
}

.help-section-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.help-section-header > span:nth-child(2) {
  flex: 1;
}
.help-section-desc {
  margin: 0 0 1rem;
  color: #71717a;
  font-size: 0.875rem;
  font-style: italic;
}

.faq-list {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.faq-item dt {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 0.9375rem;
  margin-bottom: 0.25rem;
}
.faq-item dt i {
  color: #059669;
  margin-top: 0.15rem;
  flex-shrink: 0;
}
.faq-item dd {
  margin: 0 0 0  1.5rem;
  color: #3f3f46;
  font-size: 0.9375rem;
  line-height: 1.5;
}

@media (max-width: 48rem) {
  .help-flow {
    flex-direction: column;
    align-items: stretch;
  }
  .flow-arrow {
    transform: rotate(90deg);
    align-self: center;
  }
}

@media (prefers-color-scheme: dark) {
  .help-subtitle {
    color: #a1a1aa;
  }
  .help-flow {
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.08);
  }
  .flow-step {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.06);
  }
  .flow-step i {
    color: #34d399;
  }
  .flow-arrow {
    color: #71717a;
  }
  .help-section-desc {
    color: #a1a1aa;
  }
  .faq-item dt i {
    color: #34d399;
  }
  .faq-item dd {
    color: #d4d4d8;
  }
}
</style>