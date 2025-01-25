import * as cartDemo from "cart-demo/index.js";
import { parseJson, JsonTextProducer, NullInterchangeContext } from "@temperlang/std/json";

console.log("Loaded js");

// The local view of the cart
let cart = new cartDemo.Cart(
  [],
  new cartDemo.Location(null, new cartDemo.Marks(null, 0)),
);
let lastSMark = 0;

// Changes that haven't been successfully sent to the server.
// We accumulate these to avoid data loss from network outage,
// going offline, or user airplane mode.
// TODO: could retrieve from local storage
let localCartDelta = new cartDemo.CartDelta([], cart.loc);

// Adds a delta to the cart and refreshes the view
export function applyCartDelta(cartDelta, sMark = null) {
  console.log("In applyCartDelta got", cartDelta);
  if (cartDelta instanceof cartDemo.CartDelta) {
    // Already got it.
  } else {
    // Parse JSON
    let jsonTree = parseJson(cartDelta);
    console.log('jsonTree', jsonTree);
    cartDelta = cartDemo.CartDelta.jsonAdapter().decodeFromJson(jsonTree);
    console.log('cartDelta', cartDelta);
  }
  let newCart = cartDemo.mergeCart(cart, cartDelta);
  console.log('newCart', newCart, 'sMark', sMark);
  setCart(newCart, sMark);
};

// UI updates are scheduled on the event loop, so
// multiple rapid fire changes get merged into one.
// This boolean tracks whether there's a change not
// yet reflected in the update.
let viewUpdateNeeded = false;
function setCart(newCart, sMark) {
  // Store the new cart.
  cart = newCart;
  if (typeof sMark === "number") {
    lastSMark = sMark;
  }
  viewUpdateNeeded = true;
  setTimeout(updateCartView);
};

function updateCartView() {
  if (!viewUpdateNeeded) return;
  viewUpdateNeeded = false;

  let cartTable = document.querySelector('#cart-table');
  let tbody = cartTable.querySelector('tbody');
  if (!tbody) {
    // Style it like it's 1999.
    cartTable.setAttribute('border', '1');
    // Build a space for error messages
    let errorMessageList = document.createElement('ul');
    errorMessageList.id = 'error-message-list';
    cartTable.parentElement.insertBefore(errorMessageList, cartTable);
    // Build a fake "disable network" checkbox so we can demo
    // reconciliation.
    let networkCheckboxContainer = document.createElement('label');
    networkCheckboxContainer.innerHTML =
      '<input id="network-enabled" type=checkbox checked> network enabled';
    cartTable.parentElement.insertBefore(networkCheckboxContainer, cartTable);
    // Build the initial headers and body
    let thead = document.createElement('thead');
    {
      let tr = document.createElement('tr');
      for (let header of ['SKU', 'Description', 'Count', 'Price', 'Status']) {
        let th = document.createElement('th');
        tr.appendChild(th);
        th.textContent = header;
      }
      thead.appendChild(tr);
    }
    tbody = document.createElement('tbody');
    let tfoot = document.createElement('tfoot');
    // Build a footer row for the total
    {
      let tr = document.createElement('tr');
      tr.innerHTML = '<td></td><td></td><td></td><td id=cart-total></td><td></td>';
      tfoot.appendChild(tr);
    }
    // Build a footer row for the new-item input
    {
      let tr = document.createElement('tr');
      let skuTd = document.createElement('td');
      skuTd.setAttribute('colspan', '5');
      skuTd.innerHTML =
        '<hr><input name=newsku> <button id=add-sku-button>Add To Cart</button>';
      tr.appendChild(skuTd);
      onClick(
        skuTd.querySelector('button'),
        addSkuFromInput.bind(null, skuTd.querySelector('input'))
      );
      tfoot.appendChild(tr);
    }
    // Build a footer row for the location input
    {
      let tr = document.createElement('tr');
      let locTd = document.createElement('td');
      locTd.setAttribute('colspan', '5');
      locTd.innerHTML =
        '<hr><input id=loc-input> <button id=update-loc>Update location</button>';
      tr.appendChild(locTd);
      onClick(
        locTd.querySelector('button'),
        updateLocFromInput.bind(null, locTd.querySelector('input'))
      );
      tfoot.appendChild(tr);
    }

    cartTable.appendChild(thead);
    cartTable.appendChild(tbody);
    cartTable.appendChild(tfoot);
  }

  // Gather relationships between entries and skus
  let skusInCart = new Set();
  for (let entry of cart.entries) {
    if (entry.count) { // Not a zero-count tombstone
      skusInCart.add(entry.sku);
    }
  }

  let rowsBySku = new Map();
  let unmentionedRows = [];
  for (let row of tbody.querySelectorAll('tr')) {
    let sku = row.getAttribute('data-sku');
    if (skusInCart.has(sku)) {
      rowsBySku.set(sku, row);
    } else {
      unmentionedRows.push(row);
    }
  }

  for (let entry of cart.entries) {
    let sku = entry.sku;
    // Leave out empty rows.
    if (!skusInCart.has(sku)) { continue }

    // Create row as needed.
    let row = rowsBySku.get(sku);
    if (!row) {
      row = document.createElement('tr');
      row.setAttribute('data-sku', sku);
      row.innerHTML = `<th class=sku></th>
        <td class=desc></td>
        <td><input type=number class=count> <button>update</button></td>
        <td class=price></td>
        <td class=status></td>`;
      tbody.appendChild(row);
      onClick(row.querySelector('button'),
              updateCountForSku.bind(null, sku, row.querySelector('.count')));
    }

    // Update row
    let { stocked, count } = entry;

    row.querySelector('.sku').textContent = sku;
    row.querySelector('.desc').textContent = entry.description;
    row.querySelector('.count').value = count;
    let unitPrice = stocked instanceof cartDemo.Stocked ? stocked.price : null;
    let totalPrice = null;
    if (unitPrice) {
      totalPrice = new cartDemo.Price(
        unitPrice.currencyCode,
        unitPrice.amount * count
      );
    }
    row.querySelector('.price').textContent =
      totalPrice?.toString() ?? "unknown";
    row.querySelector('.status').textContent =
      stocked instanceof cartDemo.UnknownStockedStatus
      ? "unknown"
      : stocked instanceof cartDemo.Stocked
      ? (stocked.available ? "" : "unavailable")
      : "ERROR";
  }

  // Retire rows that are no longer needed.
  for (let row of unmentionedRows) {
    row.parentElement.removeChild(row);
  }

  // Set the location
  let locationInput = document.querySelector('#loc-input');
  locationInput.value = cart.loc.postalCode || '';

  // Update the error message list.
  let errorMessageList = document.querySelector('#error-message-list');
  while (errorMessageList.firstChild) {
    errorMessageList.removeChild(errorMessageList.firstChild);
  }
  for (let problem of cart.problems) {
    let li = document.createElement('li');
    let message = problem.message;
    if (problem.sku) {
      message = message.replace(
        /\{\{\}\}/,
        `SKU-${problem.sku} ${
          new cartDemo.CartEntry(
            problem.sku, 1, null,
            new cartDemo.Marks(null, 0)
          ).description
        }`
      );
    }
    li.textContent = message;
    errorMessageList.appendChild(li);
  }

  // Update the total price
  let total = cart.totalOrNull;
  let totalCell = document.querySelector('#cart-total');
  totalCell.textContent = `Total: ${
    // If we have a total, print it.
    // If there're no entries, it's 0, independent of currencies.
    // Otherwise "unknown".
    total ? total.toString() : cart.entries.length ? 'unknown' : '0'
  }`;
}

function applyUserChange(cartDelta) {
  console.log('localCartDelta before', localCartDelta, 'cartDelta', cartDelta);
  localCartDelta = localCartDelta.plus(cartDelta);
  console.log('localCartDelta after', localCartDelta);
  scheduleSendToServer();
  applyCartDelta(cartDelta);
}

function updateCountForSku(sku, countInput) {
  let newCount = countInput.value;
  let entryDelta = localCartDelta.entryDeltas.find((x) => x.sku === sku);
  if (entryDelta?.count === newCount) { return }
  let cMark = currentCMark();
  let newEntryDelta = new cartDemo.CartEntryDelta(
    sku,
    newCount,
    entryDelta?.stocked || null,
    new cartDemo.Marks(null, cMark),
  );
  let delta = new cartDemo.CartDelta([newEntryDelta], null);
  applyUserChange(delta);
}

function addSkuFromInput(skuInput) {
  let sku = skuInput.value;
  if (sku) {
    console.log('adding sku: 1');
    let cMark = currentCMark();
    let delta = new cartDemo.CartDelta(
      [
        new cartDemo.CartEntryDelta(
          sku, 1, null,
          new cartDemo.Marks(null, cMark),
        ),
      ],
      null,
    );
    applyUserChange(delta);
  }
}

function updateLocFromInput(locInput) {
  let newPostalCode = locInput.value?.trim() || null;
  let cMark = currentCMark();
  let delta = new cartDemo.CartDelta(
    [],
    new cartDemo.Location(newPostalCode, new cartDemo.Marks(null, cMark)),
  );
  applyUserChange(delta);
}

function onClick(clickableElement, action) {
  clickableElement.addEventListener(
    "click", () => { action(); },
  );
}

// Send messages to the server and periodically
// poll it for external changes.
let needToSendLocalEditsToServer = false;

function scheduleSendToServer() {
  needToSendLocalEditsToServer = true;
  setTimeout(sendToServer);
}

function sendToServer() {
  if (!needToSendLocalEditsToServer) return;
  needToSendLocalEditsToServer = false;

  let deltaToSend = localCartDelta;
  let pingToSend = new cartDemo.Ping(deltaToSend, lastSMark);
  let requestBodyOut = new JsonTextProducer();
  cartDemo.Ping.jsonAdapter().encodeToJson(pingToSend, requestBodyOut);

  let { protocol, host, pathname } = document.location;
  let url = `${protocol}//${host}/update-cart`;

  async function doFetch() {
    // There's a checkbox that fakes network disruption so that we
    // can demo going offline despite the fact that 127.0.0.1 is always
    // available.
    let networkEnabled =
        document.querySelector('input#network-enabled').checked;
    let responsePromise =
      networkEnabled
      ? fetch(
          url,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: requestBodyOut.toJsonString()
          }
        )
      : (async () => {
          console.log('Simulated network disruption');
          return { ok: false, networkFakeDisabled: true };
        })();
    let response = await responsePromise;
    if (response.ok) {
      console.log('ok response for local edits', response);
      setTimeout(
        localEditsProcessed.bind(null, deltaToSend, response.text())
      );
    } else {
      console.error('!ok response for local edits', response);
    }
  }
  doFetch();
}

async function localEditsProcessed(editsProcessed, pingBodyPromise) {
  let before = localCartDelta;
  localCartDelta = localCartDelta.minus(editsProcessed);
  let after = localCartDelta;
  console.log('after processing', before, '->', after);

  let responseJson = await pingBodyPromise;
  console.log('got ping response', responseJson);
  let responsePing = cartDemo.Ping.jsonAdapter().decodeFromJson(
    parseJson(responseJson),
    NullInterchangeContext.instance,
  );
  applyCartDelta(responsePing.cartDelta, responsePing.sMark);
}

function currentCMark() {
  return Date.now() & 0x7FFFFFFF;
}

// Periodically ping the server for updates made by other clients.
setInterval(
  () => {
    console.log('scheduling ping to server for updates');
    scheduleSendToServer();
  },
  10000 /* ms */
);
