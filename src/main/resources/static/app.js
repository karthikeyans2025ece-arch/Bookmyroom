// BookMyRoom frontend - plain JavaScript, no libraries.
// It talks to the Spring Boot REST API on the same server using relative URLs (/api/...).

'use strict';

/* =====================================================================
   1. Small helpers
   ===================================================================== */

const $ = (id) => document.getElementById(id);

// Escape text before putting it into HTML so user input can't break the page.
function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const pad = (n) => String(n).padStart(2, '0');

// Dates are kept as "YYYY-MM-DD" strings (the format LocalDate expects in the backend).
function today() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function addDays(iso, days) {
  const [y, m, d] = iso.split('-').map(Number);
  const t = new Date(y, m - 1, d + days);
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`;
}

function nightsBetween(checkIn, checkOut) {
  if (!checkIn || !checkOut) return 0;
  const [y1, m1, d1] = checkIn.split('-').map(Number);
  const [y2, m2, d2] = checkOut.split('-').map(Number);
  const diff = (Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86400000;
  return diff > 0 ? diff : 0;
}

function fmtDate(iso) {
  if (!iso) return '-';
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fmtDateTime(value) {
  if (!value) return '-';
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const money = (v) => moneyFormat.format(Number(v) || 0);

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];

function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate(); // month is 1-12
}

/* =====================================================================
   2. Talking to the backend
   ===================================================================== */

// The backend sends errors as { timestamp, status, error, message }.
function errorText(status, body) {
  if (status >= 500) return 'Something went wrong on the server. Please try again later.';
  if (body && body.message) return body.message;
  if (status === 404) return 'The requested record was not found.';
  if (status === 409) return 'This action conflicts with existing data.';
  return 'Some of the entered data is not valid.';
}

async function api(method, path, body) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    throw new Error('Cannot reach the server. Please check that BookMyRoom is running on http://localhost:8080.');
  }

  if (response.status === 204) return null; // DELETE returns no body

  let data = null;
  try { data = await response.json(); } catch (e) { /* no JSON body */ }

  if (!response.ok) throw new Error(errorText(response.status, data));
  return data;
}

/* =====================================================================
   3. Toast messages and pop-up dialogs
   ===================================================================== */

function toast(text, type) {
  const el = document.createElement('div');
  el.className = 'toast' + (type === 'error' ? ' error' : '');
  el.textContent = text;
  $('toasts').appendChild(el);
  setTimeout(() => el.remove(), 4000);
}

function showError(id, text) {
  const box = $(id);
  box.textContent = text || '';
  box.classList.toggle('show', Boolean(text));
}

let onModalClose = null;

function openModal(title, html) {
  onModalClose = null;
  $('modalTitle').textContent = title;
  $('modalBody').innerHTML = html;
  $('modal').classList.remove('hidden');
}

function closeModal() {
  $('modal').classList.add('hidden');
  $('modalBody').innerHTML = '';
  if (onModalClose) {
    const callback = onModalClose;
    onModalClose = null;
    callback();
  }
}

// Resolves to true if the user confirms, false otherwise.
function confirmBox(title, message, okText) {
  return new Promise((resolve) => {
    openModal(title, `
      <p>${esc(message)}</p>
      <div class="actions">
        <button class="btn light" id="confirmNo">Keep it</button>
        <button class="btn danger" id="confirmYes">${esc(okText)}</button>
      </div>`);
    onModalClose = () => resolve(false);
    $('confirmNo').onclick = closeModal;
    $('confirmYes').onclick = () => {
      onModalClose = null;
      closeModal();
      resolve(true);
    };
  });
}

$('modalClose').onclick = closeModal;
$('modal').onclick = (e) => { if (e.target === $('modal')) closeModal(); };

/* =====================================================================
   4. Data kept in memory and the shared table helper
   ===================================================================== */

const state = { rooms: [], guests: [], bookings: [] };

const guestName = (id) => {
  const guest = state.guests.find((g) => g.id === id);
  return guest ? guest.name : 'Guest #' + id;
};

function tableHtml(headers, rows, emptyTitle, emptyText) {
  if (rows.length === 0) {
    return `<div class="empty"><strong>${esc(emptyTitle)}</strong>${esc(emptyText || '')}</div>`;
  }
  const head = headers.map((h) => `<th${h.right ? ' class="right"' : ''}>${esc(h.text || h)}</th>`).join('');
  return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;
}

const statusBadge = (status) =>
  `<span class="badge ${status === 'CONFIRMED' ? 'green' : 'red'}">${esc(status)}</span>`;

// Fills a <select>, keeping the current choice if it still exists.
function fillSelect(select, items, placeholder, label) {
  const keep = select.value;
  select.innerHTML = `<option value="">${esc(placeholder)}</option>` +
    items.map((x) => `<option value="${x.id}">${esc(label(x))}</option>`).join('');
  if ([...select.options].some((o) => o.value === keep)) select.value = keep;
}

/* =====================================================================
   5. Loading data and showing pages
   ===================================================================== */

async function refresh() {
  try {
    const [rooms, guests, bookings] = await Promise.all([
      api('GET', '/api/rooms'),
      api('GET', '/api/guests'),
      api('GET', '/api/bookings'),
    ]);
    state.rooms = rooms;
    state.guests = guests;
    state.bookings = bookings;
    $('banner').innerHTML = '';
    renderAll();
    loadMonthSummary();
  } catch (e) {
    $('banner').innerHTML =
      `<div class="alert"><span>${esc(e.message)}</span><button class="btn light small" onclick="refresh()">Try again</button></div>`;
  }
}

function renderAll() {
  renderDashboard();
  renderRooms();
  renderGuests();
  renderBookingForm();
  renderBookings();
}

const pages = ['dashboard', 'rooms', 'guests', 'availability', 'book', 'bookings', 'report'];

function showPage(name) {
  if (!pages.includes(name)) name = 'dashboard';
  pages.forEach((p) => $(p).classList.toggle('active', p === name));
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.page === name));
  window.scrollTo(0, 0);
  refresh();
  if (name === 'report') loadReport();
}

function goTo(name) {
  if (location.hash === '#' + name) showPage(name);
  else location.hash = name; // triggers "hashchange"
}

document.querySelectorAll('#nav button').forEach((b) => (b.onclick = () => goTo(b.dataset.page)));
document.querySelectorAll('[data-goto]').forEach((b) => (b.onclick = () => goTo(b.dataset.goto)));
window.addEventListener('hashchange', () => showPage(location.hash.slice(1)));

/* =====================================================================
   6. Dashboard
   ===================================================================== */

function renderDashboard() {
  const t = today();
  const confirmed = state.bookings.filter((b) => b.status === 'CONFIRMED');
  const cancelled = state.bookings.filter((b) => b.status === 'CANCELLED');

  $('todayText').textContent = 'Guest house status for ' + fmtDate(t);
  $('cRooms').textContent = state.rooms.length;
  $('cInService').textContent = state.rooms.filter((r) => r.available).length;
  $('cGuests').textContent = state.guests.length;
  $('cActive').textContent = confirmed.length;
  $('cCancelled').textContent = cancelled.length;

  // a room is occupied tonight if a confirmed stay has check-in <= today < check-out
  const staying = confirmed.filter((b) => b.checkInDate <= t && b.checkOutDate > t);
  const occupied = new Set(staying.map((b) => b.roomId)).size;
  const percent = state.rooms.length ? Math.round((occupied / state.rooms.length) * 100) : 0;
  const arriving = confirmed.filter((b) => b.checkInDate === t).length;
  const leaving = confirmed.filter((b) => b.checkOutDate === t).length;

  $('occPercent').textContent = percent + '%';
  $('occBar').style.width = percent + '%';
  $('occText').textContent =
    `${occupied} of ${state.rooms.length} rooms occupied - ${arriving} arriving today - ${leaving} leaving today`;

  const upcoming = confirmed
    .filter((b) => b.checkInDate >= t)
    .sort((a, b) => a.checkInDate.localeCompare(b.checkInDate))
    .slice(0, 5);

  $('upcomingList').innerHTML = tableHtml(
    ['Reference', 'Guest', 'Room', 'Check-in', 'Check-out', 'Status'],
    upcoming.map((b) => `<tr>
      <td><strong>${esc(b.bookingReference)}</strong></td>
      <td>${esc(guestName(b.guestId))}</td>
      <td>${esc(b.roomNumber)}</td>
      <td>${fmtDate(b.checkInDate)}</td>
      <td>${fmtDate(b.checkOutDate)}</td>
      <td>${statusBadge(b.status)}</td></tr>`),
    'No upcoming check-ins');
}

async function loadMonthSummary() {
  const month = today().slice(0, 7);
  try {
    const report = await api('GET', '/api/reports/occupancy?month=' + month);
    $('monthLabel').textContent = '(' + report.month + ')';
    $('mConfirmed').textContent = report.confirmedBookings;
    $('mNights').textContent = report.occupiedRoomNights;
    $('mRevenue').textContent = money(report.totalRevenue);
  } catch (e) {
    $('mConfirmed').textContent = $('mNights').textContent = $('mRevenue').textContent = '-';
  }
}

/* =====================================================================
   7. Rooms
   ===================================================================== */

function readRoomForm(form) {
  const data = new FormData(form);
  const roomNumber = String(data.get('roomNumber')).trim();
  const capacity = Number(data.get('capacity'));
  const rate = Number(data.get('ratePerNight'));

  if (!roomNumber) return { error: 'Room number is required.' };
  if (!Number.isInteger(capacity) || capacity <= 0) return { error: 'Capacity must be a whole number greater than 0.' };
  if (!(rate > 0)) return { error: 'Rate per night must be greater than 0.' };

  return {
    payload: {
      roomNumber,
      roomType: data.get('roomType'),
      capacity,
      ratePerNight: rate,
      available: form.elements.available.checked,
    },
  };
}

function renderRooms() {
  const text = $('roomSearch').value.trim().toLowerCase();
  const type = $('roomTypeFilter').value;
  const rooms = state.rooms.filter((r) =>
    r.roomNumber.toLowerCase().includes(text) && (!type || r.roomType === type));

  $('roomList').innerHTML = tableHtml(
    ['Room no.', 'Type', 'Capacity', 'Rate / night', 'Status', { text: 'Actions', right: true }],
    rooms.map((r) => `<tr>
      <td><strong>${esc(r.roomNumber)}</strong></td>
      <td>${esc(r.roomType)}</td>
      <td>${r.capacity}</td>
      <td>${money(r.ratePerNight)}</td>
      <td><span class="badge ${r.available ? 'green' : 'grey'}">${r.available ? 'In service' : 'Unavailable'}</span></td>
      <td class="actions">
        <button class="btn light small" data-action="edit-room" data-id="${r.id}">Edit</button>
        <button class="btn outline-danger small" data-action="delete-room" data-id="${r.id}">Delete</button>
      </td></tr>`),
    state.rooms.length ? 'No room matches your search' : 'No rooms yet',
    state.rooms.length ? '' : 'Add your first room using the form above.');
}

$('roomForm').onsubmit = async (e) => {
  e.preventDefault();
  const result = readRoomForm(e.target);
  showError('roomError', result.error);
  if (result.error) return;
  try {
    await api('POST', '/api/rooms', result.payload);
    toast(`Room ${result.payload.roomNumber} added`);
    e.target.reset();
    refresh();
  } catch (err) {
    showError('roomError', err.message);
  }
};

$('roomSearch').oninput = renderRooms;
$('roomTypeFilter').onchange = renderRooms;

$('roomList').onclick = async (e) => {
  const button = e.target.closest('button[data-action]');
  if (!button) return;
  const room = state.rooms.find((r) => r.id === Number(button.dataset.id));

  if (button.dataset.action === 'edit-room') {
    openModal('Edit room ' + room.roomNumber, `
      <div id="modalError" class="form-error"></div>
      <form id="editRoomForm" class="form" novalidate>
        <label>Room number<input name="roomNumber" maxlength="10" value="${esc(room.roomNumber)}"></label>
        <label>Room type
          <select name="roomType">
            ${['SINGLE', 'DOUBLE', 'DELUXE'].map((t) => `<option ${t === room.roomType ? 'selected' : ''}>${t}</option>`).join('')}
          </select>
        </label>
        <label>Capacity<input name="capacity" type="number" min="1" value="${room.capacity}"></label>
        <label>Rate per night (&#8377;)<input name="ratePerNight" type="number" min="1" step="0.01" value="${room.ratePerNight}"></label>
        <label class="check"><input type="checkbox" name="available" ${room.available ? 'checked' : ''}> Room is in service</label>
      </form>
      <div class="actions"><button class="btn light" id="editCancel">Cancel</button><button class="btn" id="editSave">Save changes</button></div>`);

    $('editCancel').onclick = closeModal;
    $('editSave').onclick = async () => {
      const result = readRoomForm($('editRoomForm'));
      showError('modalError', result.error);
      if (result.error) return;
      try {
        await api('PUT', '/api/rooms/' + room.id, result.payload);
        closeModal();
        toast(`Room ${result.payload.roomNumber} updated`);
        refresh();
      } catch (err) {
        showError('modalError', err.message);
      }
    };
  }

  if (button.dataset.action === 'delete-room') {
    const yes = await confirmBox('Delete room',
      `Delete room ${room.roomNumber}? Rooms that already have bookings cannot be deleted.`, 'Delete room');
    if (!yes) return;
    try {
      await api('DELETE', '/api/rooms/' + room.id);
      toast(`Room ${room.roomNumber} deleted`);
      refresh();
    } catch (err) {
      toast(err.message, 'error');
    }
  }
};

/* =====================================================================
   8. Guests
   ===================================================================== */

function readGuestForm(form) {
  const data = new FormData(form);
  const guest = {
    name: String(data.get('name')).trim(),
    email: String(data.get('email')).trim(),
    phone: String(data.get('phone')).trim(),
    address: String(data.get('address')).trim(),
  };
  if (!guest.name) return { error: 'Name is required.' };
  if (!guest.email) return { error: 'Email is required.' };
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(guest.email)) return { error: 'Enter a valid email address.' };
  if (!guest.phone) return { error: 'Phone number is required.' };
  if (!/^[0-9+\-\s]{7,20}$/.test(guest.phone)) return { error: 'Enter a valid phone number.' };
  return { payload: guest };
}

function renderGuests() {
  const text = $('guestSearch').value.trim().toLowerCase();
  const guests = state.guests.filter((g) =>
    g.name.toLowerCase().includes(text) || g.email.toLowerCase().includes(text) || g.phone.includes(text));

  $('guestList').innerHTML = tableHtml(
    ['ID', 'Name', 'Email', 'Phone', 'Address', { text: 'Actions', right: true }],
    guests.map((g) => `<tr>
      <td>${g.id}</td>
      <td><strong>${esc(g.name)}</strong></td>
      <td>${esc(g.email)}</td>
      <td>${esc(g.phone)}</td>
      <td>${esc(g.address) || '-'}</td>
      <td class="actions">
        <button class="btn light small" data-action="edit-guest" data-id="${g.id}">Edit</button>
        <button class="btn outline-danger small" data-action="delete-guest" data-id="${g.id}">Delete</button>
      </td></tr>`),
    state.guests.length ? 'No guest matches your search' : 'No guests yet',
    state.guests.length ? '' : 'Add a guest before creating a booking.');
}

$('guestForm').onsubmit = async (e) => {
  e.preventDefault();
  const result = readGuestForm(e.target);
  showError('guestError', result.error);
  if (result.error) return;
  try {
    await api('POST', '/api/guests', result.payload);
    toast(`${result.payload.name} added`);
    e.target.reset();
    refresh();
  } catch (err) {
    showError('guestError', err.message);
  }
};

$('guestSearch').oninput = renderGuests;

$('guestList').onclick = async (e) => {
  const button = e.target.closest('button[data-action]');
  if (!button) return;
  const guest = state.guests.find((g) => g.id === Number(button.dataset.id));

  if (button.dataset.action === 'edit-guest') {
    openModal('Edit ' + guest.name, `
      <div id="modalError" class="form-error"></div>
      <form id="editGuestForm" class="form" novalidate>
        <label>Full name<input name="name" value="${esc(guest.name)}"></label>
        <label>Email<input name="email" type="email" value="${esc(guest.email)}"></label>
        <label>Phone<input name="phone" value="${esc(guest.phone)}"></label>
        <label>Address<input name="address" value="${esc(guest.address)}"></label>
      </form>
      <div class="actions"><button class="btn light" id="editCancel">Cancel</button><button class="btn" id="editSave">Save changes</button></div>`);

    $('editCancel').onclick = closeModal;
    $('editSave').onclick = async () => {
      const result = readGuestForm($('editGuestForm'));
      showError('modalError', result.error);
      if (result.error) return;
      try {
        await api('PUT', '/api/guests/' + guest.id, result.payload);
        closeModal();
        toast(`${result.payload.name} updated`);
        refresh();
      } catch (err) {
        showError('modalError', err.message);
      }
    };
  }

  if (button.dataset.action === 'delete-guest') {
    const yes = await confirmBox('Delete guest',
      `Delete ${guest.name}? Guests who already have bookings cannot be deleted.`, 'Delete guest');
    if (!yes) return;
    try {
      await api('DELETE', '/api/guests/' + guest.id);
      toast(`${guest.name} deleted`);
      refresh();
    } catch (err) {
      toast(err.message, 'error');
    }
  }
};

/* =====================================================================
   9. Availability
   ===================================================================== */

$('availIn').value = today();
$('availOut').value = addDays(today(), 1);

$('availForm').onsubmit = async (e) => {
  e.preventDefault();
  const checkIn = $('availIn').value;
  const checkOut = $('availOut').value;
  showError('availError', '');

  if (!checkIn || !checkOut) return showError('availError', 'Please select both check-in and check-out dates.');
  const nights = nightsBetween(checkIn, checkOut);
  if (nights === 0) return showError('availError', 'Check-out date must be after check-in date.');

  $('availResult').innerHTML = '<div class="panel empty">Checking rooms...</div>';
  try {
    // free rooms come from the availability endpoint; the full list is only used
    // to show which rooms are NOT free
    const [free, all] = await Promise.all([
      api('GET', `/api/rooms/availability?checkIn=${checkIn}&checkOut=${checkOut}`),
      api('GET', '/api/rooms'),
    ]);
    const freeIds = new Set(free.map((r) => r.id));
    const notFree = all.filter((r) => !freeIds.has(r.id));

    let html = `<div class="panel"><h3>Available rooms
      <span class="muted small"> ${fmtDate(checkIn)} to ${fmtDate(checkOut)} (${nights} ${nights === 1 ? 'night' : 'nights'})</span></h3>`;
    html += tableHtml(
      ['Room no.', 'Type', 'Capacity', 'Rate / night', 'Cost for stay', 'Status', ''],
      free.map((r) => `<tr>
        <td><strong>${esc(r.roomNumber)}</strong></td>
        <td>${esc(r.roomType)}</td>
        <td>${r.capacity}</td>
        <td>${money(r.ratePerNight)}</td>
        <td>${money(r.ratePerNight * nights)}</td>
        <td><span class="badge green">Available</span></td>
        <td class="actions"><button class="btn small" data-book="${r.id}">Book</button></td></tr>`),
      'No rooms available', 'Every room is booked or out of service for these dates. Try different dates.');
    html += '</div>';

    if (notFree.length > 0) {
      html += `<div class="panel"><h3>Not available for these dates</h3>` + tableHtml(
        ['Room no.', 'Type', 'Capacity', 'Rate / night', 'Status'],
        notFree.map((r) => `<tr>
          <td>${esc(r.roomNumber)}</td><td>${esc(r.roomType)}</td><td>${r.capacity}</td>
          <td>${money(r.ratePerNight)}</td>
          <td><span class="badge grey">${r.available ? 'Booked' : 'Out of service'}</span></td></tr>`),
        '').replace('<table>', '<table class="faded">') + '</div>';
    }
    $('availResult').innerHTML = html;
    $('availResult').dataset.checkIn = checkIn;
    $('availResult').dataset.checkOut = checkOut;
  } catch (err) {
    $('availResult').innerHTML = '';
    showError('availError', err.message);
  }
};

// "Book" button: open the booking form with room and dates already filled in
$('availResult').onclick = (e) => {
  const button = e.target.closest('button[data-book]');
  if (!button) return;
  $('bookRoom').value = button.dataset.book;
  $('bookIn').value = $('availResult').dataset.checkIn;
  $('bookOut').value = $('availResult').dataset.checkOut;
  resetReceipt();
  updateSummary();
  goTo('book');
};

/* =====================================================================
   10. New booking
   ===================================================================== */

$('bookIn').value = today();
$('bookOut').value = addDays(today(), 1);

function renderBookingForm() {
  fillSelect($('bookGuest'), state.guests, 'Select guest', (g) => `${g.name} (${g.phone})`);
  fillSelect($('bookRoom'), state.rooms.filter((r) => r.available), 'Select room',
    (r) => `${r.roomNumber} - ${r.roomType} (max ${r.capacity})`);
  updateSummary();
}

function selectedRoom() {
  return state.rooms.find((r) => String(r.id) === $('bookRoom').value);
}

function updateSummary() {
  const room = selectedRoom();
  const nights = nightsBetween($('bookIn').value, $('bookOut').value);
  $('sumRoom').textContent = room ? `${room.roomNumber} (${room.roomType})` : '-';
  $('sumRate').textContent = room ? money(room.ratePerNight) : '-';
  $('sumNights').textContent = nights || '-';
  $('sumTotal').textContent = room && nights ? money(room.ratePerNight * nights) : '-';
}

['bookRoom', 'bookIn', 'bookOut'].forEach((id) => ($(id).onchange = updateSummary));

function resetReceipt() {
  $('receipt').classList.add('hidden');
  $('bookWrap').classList.remove('hidden');
}

$('bookForm').onsubmit = async (e) => {
  e.preventDefault();
  showError('bookError', '');

  const guestId = $('bookGuest').value;
  const roomId = $('bookRoom').value;
  const checkInDate = $('bookIn').value;
  const checkOutDate = $('bookOut').value;
  const numberOfGuests = Number($('bookCount').value);
  const room = selectedRoom();

  if (!guestId) return showError('bookError', 'Please select a guest.');
  if (!roomId) return showError('bookError', 'Please select a room.');
  if (!checkInDate || !checkOutDate) return showError('bookError', 'Please select both dates.');
  if (nightsBetween(checkInDate, checkOutDate) === 0) return showError('bookError', 'Check-out date must be after check-in date.');
  if (!Number.isInteger(numberOfGuests) || numberOfGuests <= 0) return showError('bookError', 'Number of guests must be at least 1.');
  if (room && numberOfGuests > room.capacity) {
    return showError('bookError', `Room ${room.roomNumber} holds at most ${room.capacity} guests.`);
  }

  const submitButton = e.target.querySelector('button');
  submitButton.disabled = true;
  try {
    // dates are sent as plain YYYY-MM-DD strings; the server calculates the total cost
    const booking = await api('POST', '/api/bookings', {
      guestId: Number(guestId),
      roomId: Number(roomId),
      checkInDate,
      checkOutDate,
      numberOfGuests,
    });
    showReceipt(booking);
    toast(`Booking ${booking.bookingReference} confirmed`);
    refresh();
  } catch (err) {
    // e.g. "Room 101 is already booked from 2026-10-10 to 2026-10-15."
    showError('bookError', err.message);
  } finally {
    submitButton.disabled = false;
  }
};

function showReceipt(b) {
  const nights = nightsBetween(b.checkInDate, b.checkOutDate);
  $('bookWrap').classList.add('hidden');
  $('receipt').classList.remove('hidden');
  $('receipt').innerHTML = `
    <div class="ok">Booking confirmed</div>
    <div class="head">
      <div><div class="muted small">Booking reference</div><div class="ref">${esc(b.bookingReference)}</div></div>
      ${statusBadge(b.status)}
    </div>
    <dl class="details">
      <div><dt>Guest</dt><dd>${esc(guestName(b.guestId))}</dd></div>
      <div><dt>Room</dt><dd>${esc(b.roomNumber)}</dd></div>
      <div><dt>Check-in</dt><dd>${fmtDate(b.checkInDate)}</dd></div>
      <div><dt>Check-out</dt><dd>${fmtDate(b.checkOutDate)}</dd></div>
      <div><dt>Nights</dt><dd>${nights}</dd></div>
      <div><dt>Guests</dt><dd>${b.numberOfGuests}</dd></div>
      <div><dt>Total cost</dt><dd class="strong">${money(b.totalCost)}</dd></div>
    </dl>
    <div class="form-actions">
      <button class="btn light" id="anotherBooking">Make another booking</button>
      <button class="btn" id="viewBookings">View all bookings</button>
    </div>`;
  $('anotherBooking').onclick = () => { $('bookGuest').value = ''; $('bookCount').value = 1; resetReceipt(); updateSummary(); };
  $('viewBookings').onclick = () => { resetReceipt(); goTo('bookings'); };
}

/* =====================================================================
   11. Bookings list and cancellation
   ===================================================================== */

function renderBookings() {
  const text = $('bookingSearch').value.trim().toLowerCase();
  const status = $('bookingStatus').value;
  const sorted = [...state.bookings].sort((a, b) => b.id - a.id);
  const shown = sorted.filter((b) =>
    (!status || b.status === status) &&
    (b.bookingReference.toLowerCase().includes(text) ||
      b.roomNumber.toLowerCase().includes(text) ||
      guestName(b.guestId).toLowerCase().includes(text)));

  $('bookingCount').textContent = `Showing ${shown.length} of ${sorted.length}`;

  $('bookingList').innerHTML = tableHtml(
    ['Reference', 'Guest', 'Room', 'Check-in', 'Check-out', 'Guests', 'Total cost', 'Status', 'Created', { text: 'Action', right: true }],
    shown.map((b) => `<tr class="${b.status === 'CANCELLED' ? 'cancelled' : ''}">
      <td><strong>${esc(b.bookingReference)}</strong></td>
      <td>${esc(guestName(b.guestId))}</td>
      <td>${esc(b.roomNumber)}</td>
      <td class="nowrap">${fmtDate(b.checkInDate)}</td>
      <td class="nowrap">${fmtDate(b.checkOutDate)}</td>
      <td>${b.numberOfGuests}</td>
      <td class="nowrap cost">${money(b.totalCost)}</td>
      <td>${statusBadge(b.status)}</td>
      <td class="small nowrap">${fmtDateTime(b.createdAt)}</td>
      <td class="actions">${b.status === 'CONFIRMED'
        ? `<button class="btn outline-danger small" data-cancel="${b.id}">Cancel</button>`
        : '<span class="muted">-</span>'}</td></tr>`),
    sorted.length ? 'No booking matches your filters' : 'No bookings yet',
    sorted.length ? '' : 'Bookings you create will appear here.');
}

$('bookingSearch').oninput = renderBookings;
$('bookingStatus').onchange = renderBookings;

$('bookingList').onclick = async (e) => {
  const button = e.target.closest('button[data-cancel]');
  if (!button) return;
  const booking = state.bookings.find((b) => b.id === Number(button.dataset.cancel));

  const yes = await confirmBox('Cancel booking',
    `Cancel booking ${booking.bookingReference} for ${guestName(booking.guestId)} ` +
    `(room ${booking.roomNumber}, ${fmtDate(booking.checkInDate)} to ${fmtDate(booking.checkOutDate)})? ` +
    'The room becomes free for these dates.', 'Cancel booking');
  if (!yes) return;

  try {
    await api('PUT', `/api/bookings/${booking.id}/cancel`);
    toast(`Booking ${booking.bookingReference} cancelled`);
  } catch (err) {
    toast(err.message, 'error');
  }
  refresh();
};

/* =====================================================================
   12. Monthly report
   ===================================================================== */

(function setupReportSelects() {
  const now = new Date();
  $('repMonth').innerHTML = MONTHS.map((m, i) => `<option value="${i + 1}">${m}</option>`).join('');
  $('repMonth').value = now.getMonth() + 1;
  let years = '';
  for (let y = now.getFullYear() - 3; y <= now.getFullYear() + 2; y++) years += `<option>${y}</option>`;
  $('repYear').innerHTML = years;
  $('repYear').value = now.getFullYear();
})();

function barHtml(label, value, max, colour) {
  const width = max > 0 ? Math.round((value / max) * 100) : 0;
  return `<div class="bar-row"><div>${label}</div>
    <div class="bar-track"><div class="bar-fill ${colour}" style="width:${width}%"></div></div>
    <div class="bar-val">${value}</div></div>`;
}

function statCard(label, value, colour, hint) {
  return `<div class="stat ${colour}"><span>${label}</span><strong>${esc(value)}</strong>${hint ? `<small>${hint}</small>` : ''}</div>`;
}

async function loadReport() {
  const month = Number($('repMonth').value);
  const year = Number($('repYear').value);
  const param = `${year}-${pad(month)}`;
  $('reportResult').innerHTML = '<div class="panel empty">Loading report...</div>';

  try {
    const r = await api('GET', '/api/reports/occupancy?month=' + param);

    // These two numbers are not in the backend response, so they are calculated here.
    const possible = r.totalRooms * daysInMonth(year, month);
    const availableNights = Math.max(possible - r.occupiedRoomNights, 0);
    const occupancy = possible > 0 ? (r.occupiedRoomNights / possible) * 100 : 0;
    const maxBookings = Math.max(r.confirmedBookings, r.cancelledBookings);

    $('reportResult').innerHTML = `
      <div class="cards four">
        ${statCard('Total rooms', r.totalRooms, 'blue')}
        ${statCard('Rooms booked', r.bookedRooms, 'teal', r.availableRooms + ' never booked')}
        ${statCard('Occupied room nights', r.occupiedRoomNights, 'purple')}
        ${statCard('Available room nights', availableNights, 'green', 'Calculated')}
        ${statCard('Occupancy', occupancy.toFixed(1) + '%', 'blue', 'Calculated')}
        ${statCard('Confirmed bookings', r.confirmedBookings, 'teal')}
        ${statCard('Cancelled bookings', r.cancelledBookings, 'red')}
        ${statCard('Revenue', money(r.totalRevenue), 'green', 'Confirmed bookings')}
      </div>
      <div class="two-col">
        <div class="panel"><h3>Room nights - ${MONTHS[month - 1]} ${year}</h3>
          ${barHtml('Occupied', r.occupiedRoomNights, possible, '')}
          ${barHtml('Available', availableNights, possible, 'grey')}
        </div>
        <div class="panel"><h3>Bookings</h3>
          ${barHtml('Confirmed', r.confirmedBookings, maxBookings, 'green')}
          ${barHtml('Cancelled', r.cancelledBookings, maxBookings, 'red')}
        </div>
      </div>
      <p class="muted small note">Available room nights and occupancy are calculated in the browser:
      occupancy = occupied room nights / (total rooms x days in month). All other numbers come from the server.</p>`;
  } catch (err) {
    $('reportResult').innerHTML = `<div class="form-error show">${esc(err.message)}</div>`;
  }
}

$('repMonth').onchange = loadReport;
$('repYear').onchange = loadReport;

/* =====================================================================
   13. Start
   ===================================================================== */

showPage(location.hash.slice(1));
