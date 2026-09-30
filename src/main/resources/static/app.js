'use strict';
const $ = id => document.getElementById(id);
const state = { token: null, me: null, view: 'agenda', registration: false, page: 0, records: [], professionals: [], services: [], clients: [] };
const labels = { agenda: 'Agenda', clients: 'Clientes', professionals: 'Profissionais', services: 'Serviços', settings: 'Disponibilidade', users: 'Equipe e acessos' };
const statuses = { PENDING: 'Pendente', CONFIRMED: 'Confirmado', COMPLETED: 'Concluído', CANCELED: 'Cancelado', NO_SHOW: 'Não compareceu' };
const roles = { ADMIN: 'Administradora', ATTENDANT: 'Atendente', PROFESSIONAL: 'Profissional' };
const weekdays = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
let toastTimer, editorSave;
function el(tag, text, className) { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; if (className) node.className = className; return node; }
function toast(text, failure = false) { $('toast').textContent = text; $('toast').classList.toggle('failure', failure); $('toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').hidden = true, 6500); }
function on(node, event, action, errorNode) { node.addEventListener(event, async e => { try { await action(e); } catch (err) { if (errorNode) $(errorNode).textContent = err.message; else toast(err.message, true); } }); }
async function api(path, method = 'GET', data) {
 const headers = { 'Content-Type': 'application/json' }; if (state.token) headers.Authorization = `Bearer ${state.token}`;
 const response = await fetch('/api' + path, { method, headers, body: data === undefined ? undefined : JSON.stringify(data) });
 const body = response.status === 204 ? null : await response.json().catch(() => ({}));
 if (!response.ok) { if (response.status === 401 && state.me) logout(); throw new Error(body.message || `Falha na requisição (${response.status}).`); }
 return body;
}
function logout() { state.token = null; state.me = null; $('workspace').hidden = true; $('auth').hidden = false; document.querySelectorAll('dialog[open]').forEach(d => d.close()); $('auth-form').elements.password.value = ''; }
function authMode(registration) {
 state.registration = registration; $('registration-fields').hidden = !registration;
 for (const name of ['companyName', 'name', 'timezone']) $('auth-form').elements[name].required = registration;
 $('auth-form').elements.password.minLength = registration ? 12 : 1;
 $('auth-form').elements.password.autocomplete = registration ? 'new-password' : 'current-password';
 $('login-tab').classList.toggle('selected', !registration); $('register-tab').classList.toggle('selected', registration);
 $('auth-title').textContent = registration ? 'Crie seu espaço' : 'Entre no seu espaço';
 $('auth-submit').textContent = registration ? 'Criar empresa e começar →' : 'Entrar na minha agenda →'; $('auth-error').textContent = '';
}
on($('login-tab'), 'click', () => authMode(false)); on($('register-tab'), 'click', () => authMode(true));
on($('auth-form'), 'submit', async e => {
 e.preventDefault(); $('auth-error').textContent = ''; const form = e.currentTarget; const input = Object.fromEntries(new FormData(form));
 if (!state.registration) for (const key of ['companyName', 'name', 'timezone']) delete input[key];
 $('auth-submit').disabled = true;
 try { const result = await api(state.registration ? '/auth/register' : '/auth/login', 'POST', input); state.token = result.accessToken; await enter(); form.elements.password.value = ''; }
 finally { $('auth-submit').disabled = false; }
}, 'auth-error');
function localDate(instant = new Date()) {
 const parts = new Intl.DateTimeFormat('en-CA', { timeZone: state.me.timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(instant);
 const values = Object.fromEntries(parts.map(p => [p.type, p.value])); return `${values.year}-${values.month}-${values.day}`;
}
function nextDate(date, amount) { const d = new Date(date + 'T12:00:00Z'); d.setUTCDate(d.getUTCDate() + amount); return d.toISOString().slice(0, 10); }
function zonedISO(value) {
 const [date, time = '00:00'] = value.split('T'); const target = Date.parse(`${date}T${time}:00Z`); let guess = target;
 const formatter = new Intl.DateTimeFormat('en-CA', { timeZone: state.me.timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
 for (let i = 0; i < 4; i++) { const p = Object.fromEntries(formatter.formatToParts(new Date(guess)).map(x => [x.type, x.value])); const shown = Date.parse(`${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}Z`); const delta = target - shown; if (!delta) return new Date(guess).toISOString(); guess += delta; }
 throw new Error('Este horário não existe no fuso da empresa. Escolha outro horário.');
}
function fmt(value, timeOnly = false) { return new Intl.DateTimeFormat('pt-BR', { timeZone: state.me.timezone, ...(timeOnly ? {} : { day: '2-digit', month: '2-digit', year: 'numeric' }), hour: '2-digit', minute: '2-digit' }).format(new Date(value)); }
function money(value) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value); }
function fill(select, rows, placeholder) { select.replaceChildren(); if (placeholder) { const option = el('option', placeholder); option.value = ''; select.append(option); } for (const row of rows) { const option = el('option', row.name); option.value = row.id; select.append(option); } }
async function all(path) { const rows = []; for (let offset = 0; offset < 10000; offset += 500) { const batch = await api(`${path}?limit=500&offset=${offset}`); rows.push(...batch); if (batch.length < 500) return rows; } throw new Error('Use a API para consultar cadastros acima de 10 mil registros.'); }
async function refreshLookups() {
 state.professionals = (await all('/professionals')).filter(p => p.active); state.services = (await all('/services')).filter(s => s.active);
 if (state.me.role !== 'PROFESSIONAL') state.clients = (await all('/clients')).filter(c => c.active);
 fill($('filter-professional'), state.me.role === 'PROFESSIONAL' ? state.professionals.filter(p => p.id === state.me.professionalId) : state.professionals, state.me.role === 'PROFESSIONAL' ? undefined : 'Todos');
 fill($('settings-professional'), state.professionals, 'Selecione um profissional');
}
async function enter() {
 state.me = await api('/auth/me'); $('auth').hidden = true; $('workspace').hidden = false;
 $('company-label').textContent = state.me.companyName; $('user-name').textContent = state.me.name; $('role-name').textContent = roles[state.me.role]; $('avatar').textContent = state.me.name.slice(0, 1).toUpperCase();
 $('timezone-label').textContent = `Horários no fuso ${state.me.timezone}`;
 document.querySelectorAll('.staff').forEach(n => n.hidden = state.me.role === 'PROFESSIONAL'); document.querySelectorAll('.admin').forEach(n => n.hidden = state.me.role !== 'ADMIN');
 $('filter-form').elements.from.value = localDate(); $('filter-form').elements.to.value = nextDate(localDate(), 7);
 await refreshLookups(); await view('agenda');
}
on($('logout'), 'click', logout);
document.querySelectorAll('[data-view]').forEach(button => on(button, 'click', () => view(button.dataset.view)));
async function view(name) {
 state.view = name; state.page = 0; $('view-title').textContent = labels[name]; document.querySelectorAll('[data-view]').forEach(n => n.classList.toggle('selected', n.dataset.view === name));
 $('agenda-view').hidden = name !== 'agenda'; $('catalog-view').hidden = ['agenda', 'settings'].includes(name); $('settings-view').hidden = name !== 'settings';
 if (name === 'agenda') await loadAgenda(); else if (name === 'settings') await loadSettings(); else await loadCatalog();
}
on($('filter-form'), 'submit', async e => { e.preventDefault(); await loadAgenda(); });
function action(text, callback) { const button = el('button', text, 'action-button'); button.type = 'button'; on(button, 'click', callback); return button; }
async function loadAgenda() {
 const values = Object.fromEntries(new FormData($('filter-form')));
 const query = new URLSearchParams({ from: zonedISO(values.from), to: zonedISO(nextDate(values.to, 1)), limit: '500' });
 if (values.professionalId) query.set('professionalId', values.professionalId); if (values.status) query.set('status', values.status);
 const rows = await api('/appointments?' + query); $('appointment-list').replaceChildren(); $('agenda-empty').hidden = rows.length > 0;
 $('stat-total').textContent = rows.length; for (const [key, status] of [['pending', 'PENDING'], ['confirmed', 'CONFIRMED'], ['completed', 'COMPLETED']]) $('stat-' + key).textContent = rows.filter(r => r.status === status).length;
 for (const row of rows) {
  const tr = el('tr'); const when = el('td'); when.append(el('strong', fmt(row.startsAt)), el('small', `Até ${fmt(row.endsAt, true)}`));
  const client = el('td'); client.append(el('strong', row.clientName), el('small', `${row.serviceName} · ${money(row.price)}`));
  const status = el('td'); status.append(el('span', statuses[row.status], 'badge ' + row.status)); const actions = el('td');
  const change = async next => { await api(`/appointments/${row.id}/status`, 'PATCH', { status: next }); await loadAgenda(); toast('Agendamento atualizado.'); };
  if (row.status === 'PENDING' && state.me.role !== 'PROFESSIONAL') actions.append(action('Confirmar', () => change('CONFIRMED')));
  if (row.status === 'CONFIRMED') { actions.append(action('Concluir', () => change('COMPLETED')), action('Falta', () => change('NO_SHOW'))); }
  if (['PENDING', 'CONFIRMED'].includes(row.status) && state.me.role !== 'PROFESSIONAL') {
   actions.append(action('Reagendar', () => editReschedule(row)), action('Cancelar', async () => { if (confirm('Cancelar este agendamento?')) await change('CANCELED'); }));
  }
  actions.append(action('Histórico', () => showHistory(row))); tr.append(when, client, el('td', row.professionalName), status, actions); $('appointment-list').append(tr);
 }
}
const fields = {
 clients: [['name', 'Nome', 'text', true], ['email', 'E-mail', 'email', false], ['phone', 'Telefone', 'tel', false]],
 professionals: [['name', 'Nome', 'text', true]],
 services: [['name', 'Nome', 'text', true], ['durationMinutes', 'Duração em minutos', 'number', true], ['price', 'Preço (R$)', 'number', true]],
 users: [['name', 'Nome', 'text', true], ['email', 'E-mail', 'email', true], ['password', 'Senha (mínimo 12 caracteres)', 'password', true], ['role', 'Permissão', 'role', true], ['professionalId', 'Profissional vinculado (somente PROFESSIONAL)', 'professional', false]]
};
async function loadCatalog() {
 const kind = state.view; const rows = await api(`/${kind}?limit=50&offset=${state.page * 50}`); state.records = rows;
 $('catalog-title').textContent = labels[kind]; $('new-record').hidden = kind !== 'clients' && state.me.role !== 'ADMIN';
 $('catalog-head').replaceChildren(); const head = el('tr'); const titles = kind === 'services' ? ['Nome', 'Duração', 'Preço', 'Situação', 'Ações'] : kind === 'users' ? ['Nome', 'E-mail', 'Permissão', 'Situação', 'Ações'] : ['Nome', kind === 'clients' ? 'Contato' : 'Identificador', 'Situação', 'Ações']; titles.forEach(t => head.append(el('th', t))); $('catalog-head').append(head);
 $('catalog-list').replaceChildren(); $('catalog-empty').hidden = rows.length > 0;
 for (const row of rows) {
  const tr = el('tr'); tr.append(el('td', row.name));
  if (kind === 'services') tr.append(el('td', `${row.durationMinutes} min`), el('td', money(row.price)));
  else if (kind === 'users') tr.append(el('td', row.email), el('td', roles[row.role]));
  else tr.append(el('td', kind === 'clients' ? [row.email, row.phone].filter(Boolean).join(' · ') : row.id));
  tr.append(el('td', row.active ? 'Ativo' : 'Inativo')); const actions = el('td');
  if ((kind === 'clients' && state.me.role !== 'PROFESSIONAL') || state.me.role === 'ADMIN') {
   if (kind !== 'users') actions.append(action('Editar', () => editRecord(row)));
   if (row.active && row.id !== state.me.id) actions.append(action('Desativar', async () => { if (!confirm(`Desativar ${row.name}? O histórico será preservado.`)) return; await api(`/${kind}/${row.id}`, 'DELETE'); await loadCatalog(); await refreshLookups(); toast('Cadastro desativado.'); }));
  }
  tr.append(actions); $('catalog-list').append(tr);
 }
 $('page-label').textContent = `Página ${state.page + 1}`; $('previous-page').disabled = state.page === 0; $('next-page').disabled = rows.length < 50;
}
on($('previous-page'), 'click', async () => { state.page--; await loadCatalog(); }); on($('next-page'), 'click', async () => { state.page++; await loadCatalog(); });
function editor(title, build, save) { $('editor-title').textContent = title; $('editor-fields').replaceChildren(); $('editor-error').textContent = ''; $('editor-submit').hidden = !save; editorSave = save; build($('editor-fields')); $('editor').showModal(); }
function field(container, name, label, type, value, required) {
 const wrapper = el('label', label); const input = el(type === 'role' || type === 'professional' ? 'select' : 'input'); input.name = name; input.required = required;
 if (type === 'role') fill(input, Object.entries(roles).map(([id, name]) => ({ id, name })));
 else if (type === 'professional') fill(input, state.professionals, 'Nenhum');
 else { input.type = type; if (type === 'number') { input.step = name === 'price' ? '.01' : '1'; input.min = name === 'price' ? '0' : '5'; input.max = name === 'price' ? '9999999999.99' : '480'; } input.maxLength = name === 'email' ? 254 : name === 'password' || name === 'newPassword' ? 72 : name === 'phone' ? 30 : 120; }
 if (type === 'password' && name !== 'currentPassword') input.minLength = 12; if (value !== undefined && value !== null) input.value = value; wrapper.append(input); container.append(wrapper); return input;
}
function editRecord(row) {
 const kind = state.view;
 editor(row ? `Editar ${row.name}` : `Adicionar · ${labels[kind]}`, box => fields[kind].forEach(([name, label, type, required]) => field(box, name, label, type, row?.[name], required)), async data => {
  if (kind === 'services') { data.durationMinutes = Number(data.durationMinutes); data.price = Number(data.price); }
  if (kind === 'clients') { if (!data.email) delete data.email; if (!data.phone) delete data.phone; }
  if (kind === 'users') { if (data.role !== 'PROFESSIONAL') delete data.professionalId; else if (!data.professionalId) throw new Error('Vincule o usuário a um profissional.'); }
  await api(`/${kind}${row ? '/' + row.id : ''}`, row ? 'PUT' : 'POST', data); await loadCatalog(); await refreshLookups(); toast('Cadastro salvo.');
 });
}
on($('new-record'), 'click', () => editRecord());
on($('editor-form'), 'submit', async e => { e.preventDefault(); if (!editorSave) return; $('editor-error').textContent = ''; $('editor-submit').disabled = true; try { await editorSave(Object.fromEntries(new FormData(e.currentTarget))); $('editor').close(); } finally { $('editor-submit').disabled = false; } }, 'editor-error');
for (const id of ['close-editor', 'cancel-editor']) on($(id), 'click', () => $('editor').close());
function editReschedule(row) { editor('Reagendar atendimento', box => { box.append(el('p', `Horário no fuso ${state.me.timezone}. A duração e o preço original serão preservados.`, 'muted')); field(box, 'startsAt', 'Novo início', 'datetime-local', undefined, true); }, async data => { await api(`/appointments/${row.id}/reschedule`, 'PATCH', { startsAt: zonedISO(data.startsAt) }); await loadAgenda(); toast('Agendamento movido.'); }); }
async function showHistory(row) { const events = await api(`/appointments/${row.id}/events`); editor('Histórico do atendimento', box => { for (const event of events) box.append(el('p', `${fmt(event.occurredAt)} · ${event.action}`, 'muted')); if (row.notes) box.append(el('p', 'Observações: ' + row.notes)); }, null); }
on($('password-button'), 'click', () => editor('Alterar senha', box => { field(box, 'currentPassword', 'Senha atual', 'password', undefined, true); field(box, 'newPassword', 'Nova senha', 'password', undefined, true); }, async data => { await api('/auth/password', 'PUT', data); logout(); toast('Senha alterada. Entre novamente com a nova senha.'); }));
async function offeredForBooking() { const id = $('booking-professional').value; fill($('booking-service'), id ? (await api(`/professionals/${id}/services`)).filter(s => s.active) : [], 'Selecione um serviço'); resetSlots(); }
function resetSlots() { fill($('booking-slot'), [], 'Consulte a disponibilidade'); }
on($('new-booking'), 'click', async () => { await refreshLookups(); if (!state.clients.length || !state.professionals.length) throw new Error('Cadastre clientes e profissionais antes de agendar.'); $('booking-form').reset(); fill($('booking-client'), state.clients, 'Selecione um cliente'); fill($('booking-professional'), state.professionals, 'Selecione um profissional'); fill($('booking-service'), [], 'Selecione um serviço'); $('booking-form').elements.date.value = localDate(); $('booking-form').elements.date.min = localDate(); resetSlots(); $('booking-error').textContent = ''; $('booking-dialog').showModal(); });
on($('booking-professional'), 'change', offeredForBooking, 'booking-error'); on($('booking-service'), 'change', resetSlots); on($('booking-form').elements.date, 'change', resetSlots);
on($('find-slots'), 'click', async () => { const data = Object.fromEntries(new FormData($('booking-form'))); if (!data.professionalId || !data.serviceId || !data.date) throw new Error('Selecione profissional, serviço e data.'); $('booking-error').textContent = ''; const slots = await api('/availability?' + new URLSearchParams({ professionalId: data.professionalId, serviceId: data.serviceId, date: data.date })); fill($('booking-slot'), slots.map(s => ({ id: s.startsAt, name: `${fmt(s.startsAt, true)} – ${fmt(s.endsAt, true)}` })), slots.length ? 'Selecione um horário' : 'Nenhum horário disponível'); }, 'booking-error');
on($('booking-form'), 'submit', async e => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.currentTarget)); delete data.date; const button = e.currentTarget.querySelector('button.primary'); button.disabled = true; try { await api('/appointments', 'POST', data); $('booking-dialog').close(); await loadAgenda(); toast('Agendamento criado.'); } finally { button.disabled = false; } }, 'booking-error');
for (const id of ['close-booking', 'cancel-booking']) on($(id), 'click', () => $('booking-dialog').close());
function hoursRow(row = { dayOfWeek: 1, startTime: '09:00', endTime: '12:00' }) {
 const wrapper = el('div', undefined, 'hours-row'); const day = el('select'); day.setAttribute('aria-label', 'Dia da semana'); fill(day, weekdays.map((name, i) => ({ id: i + 1, name }))); day.value = row.dayOfWeek;
 const start = el('input'), end = el('input'); for (const [node, name, value] of [[start, 'Início do período', row.startTime], [end, 'Fim do período', row.endTime]]) { node.type = 'time'; node.required = true; node.value = value; node.setAttribute('aria-label', name); }
 const remove = el('button', '×', 'icon-button'); remove.type = 'button'; remove.setAttribute('aria-label', 'Remover período'); on(remove, 'click', () => wrapper.remove()); wrapper.append(day, start, end, remove); $('hours-rows').append(wrapper);
}
async function loadSettings() {
 const id = $('settings-professional').value; $('hours-rows').replaceChildren(); $('offered-services').replaceChildren(); $('off-list').replaceChildren(); if (!id) return;
 const offered = await api(`/professionals/${id}/services`); const ids = new Set(offered.map(s => s.id));
 for (const service of state.services) { const label = el('label', service.name); const check = el('input'); check.type = 'checkbox'; check.value = service.id; check.checked = ids.has(service.id); label.prepend(check); $('offered-services').append(label); }
 for (const row of await api(`/professionals/${id}/working-hours`)) hoursRow(row);
 for (const row of await api(`/professionals/${id}/time-off`)) { const li = el('li'); li.append(el('span', `${fmt(row.startsAt)} — ${fmt(row.endsAt)} · ${row.reason}`), action('Remover', async () => { await api(`/professionals/${id}/time-off/${row.id}`, 'DELETE'); await loadSettings(); toast('Folga removida.'); })); $('off-list').append(li); }
}
function selectedProfessional() { const id = $('settings-professional').value; if (!id) throw new Error('Selecione um profissional.'); return id; }
on($('settings-professional'), 'change', loadSettings); on($('add-hours'), 'click', () => { selectedProfessional(); hoursRow(); });
on($('services-form'), 'submit', async e => { e.preventDefault(); const id = selectedProfessional(); const values = [...$('offered-services').querySelectorAll('input:checked')].map(i => i.value); await api(`/professionals/${id}/services`, 'PUT', values); toast('Serviços vinculados.'); });
on($('hours-form'), 'submit', async e => { e.preventDefault(); const id = selectedProfessional(); const rows = [...$('hours-rows').children].map(row => { const [day, start, end] = row.querySelectorAll('select,input'); return { dayOfWeek: Number(day.value), startTime: start.value, endTime: end.value }; }); await api(`/professionals/${id}/working-hours`, 'PUT', rows); toast('Jornada salva.'); });
on($('off-form'), 'submit', async e => { e.preventDefault(); const id = selectedProfessional(); const data = Object.fromEntries(new FormData(e.currentTarget)); data.startsAt = zonedISO(data.startsAt); data.endsAt = zonedISO(data.endsAt); await api(`/professionals/${id}/time-off`, 'POST', data); e.currentTarget.reset(); await loadSettings(); toast('Folga adicionada.'); });
authMode(false);
