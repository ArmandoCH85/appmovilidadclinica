<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Select from 'primevue/select'
import CrudView from './CrudView.vue'
import { request } from '../api/client'
import { ApiError } from '../api/client'
import { calendarExceptionsConfig } from '../resources'
import type { Calendar } from '../types'

const calendars = ref<Calendar[]>([])
const loadingCalendars = ref(false)
const calendarsError = ref('')
const selectedCalendarId = ref<number | null>(null)

onMounted(async () => {
  loadingCalendars.value = true
  try {
    const res = await request<{ items: Calendar[] }>('GET', '/admin/calendars?page=1&page_size=100')
    calendars.value = res.items
  } catch (err) {
    calendarsError.value = err instanceof ApiError ? err.message : 'No se pudieron cargar los calendarios.'
  } finally {
    loadingCalendars.value = false
  }
})
</script>

<template>
  <section class="calendar-exceptions-view">
    <h1>Excepciones de calendario</h1>

    <div class="calendar-picker">
      <label for="calendar-exceptions-calendar-select">Calendario</label>
      <Select
        id="calendar-exceptions-calendar-select"
        v-model="selectedCalendarId"
        :options="calendars"
        optionLabel="name"
        optionValue="id"
        placeholder="Elija un calendario para ver sus excepciones"
        :loading="loadingCalendars"
      />
    </div>

    <p v-if="calendarsError" role="alert" class="calendar-exceptions-error">{{ calendarsError }}</p>

    <CrudView
      v-if="selectedCalendarId !== null"
      :key="selectedCalendarId"
      :config="calendarExceptionsConfig"
      :list-path="`/admin/calendar-exceptions?calendar_id=${selectedCalendarId}`"
    />
    <p v-else>Elija un calendario arriba para ver y administrar sus excepciones.</p>
  </section>
</template>

<style scoped>
.calendar-exceptions-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.calendar-picker {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 24rem;
}
.calendar-picker label {
  font-weight: 600;
}
.calendar-exceptions-error {
  color: #b91c1c;
  margin: 0;
}
@media (prefers-color-scheme: dark) {
  .calendar-exceptions-error {
    color: #fca5a5;
  }
}
</style>
