# Merge semantics for shopping carts

A cart delta is a set of changes to a shopping cart.

The merge operation combines a cart and a delta to produce
a merged cart.

Normally, a cart starts life empty, and deltas are merged into it.

A client may merge things locally to quickly update the UI.  In
parallel, it can send the delta to the server which incorporates it
and responds with a delta including authoritative price&availability
info.

## Resolving conflicts

Sequence marks are used in values and in deltas to manage merge conflicts.
Order is important; typically, an entry with a greater sequence mark has
precedence over a lesser one.

The `0` sequence mark is a fine choice for newly created items.

In production, a monotonic timestamp (an approximation of milliseconds since epoch),
is a great choice.
Test code can just use a counter.

    export let SequenceMark = Int;

Consider the following scenario:

1. A user adds an entry on their phone at 11am which for the purposes of this example uses hours and minutes,
   `1100` as the sequence mark.  The delta looks like `{ asOf: 1100, sku: "ABCD", count: 10 }`.
2. They don't have wifi connectivity, so the phone doesn't sync to the server immediately.
3. They call a customer service representative ten minutes later who adds the entry for them, but there are only
   8 available: `{ asOf: 1110, sku: "ABCD", count: 8 }`
4. The customer-relationship management app (CRM) sends that to the server which merges the change.
5. The customer elects not to purchase right then, and later when they get connectivity their
   phone sends the stored entry, `asOf: 1100` which is less than the one from the CMS, `asOf: ...1110`,
   so the server rejects the merge and sends the authoritative version with up-to-date price and version info
   back to the app.
6. The customer sees the service representative's changes and continues their purchase.

Here's that scenario in test form:

    test("merge conflict resolved") {
      var now: SequenceMark = 1059; // Server time

      // User creates a cart via a mobile app
      var appCart = { entries: [], postalCode: "90210", asOf: 0 };

      // App syncs to server creating cart for that user account.
      var serverCart = { entries: [], postalCode: null, asOf: 0 };
      let newCartRequest = { entryDeltas: [], postalCode: "90210", asOf: now };
      serverCart = mergeCart(serverCart, newCartRequest, now);

      // 1. User adds something locally.
      now = 1100;
      let appAdditions = {
        entryDeltas: [new CartEntryDelta("ABCD", 10, null, now)],
        postalCode: null,
        asOf: now
      };
      appCart = mergeCart(appCart, appAdditions, now);
      // 2. Not merge into server yet.

      // 3. CRM makes additions and sends those to the server
      now = 1110;
      var crmAdditions = {
        entryDeltas: [new CartEntryDelta("ABCD", 8, null, now)],
        postalCode: null,
        asOf: now
      };
      // 4. Server merged CRM changes
      serverCart = mergeCart(serverCart, crmAdditions, now);

      // 5. App attempts to merge to server.
      now = 1115;
      let serverCartBeforeAppMerge = serverCart;
      serverCart = mergeCart(serverCart, appAdditions, now);
      let updatesForApp = diffCart(serverCart, serverCartBeforeAppMerge, appAdditions.asOf);
      // App receives updates from server.
      appCart = mergeCart(appCart, updatesForApp, now);

      // Both the app and the server have one entry.
      assert(appCart.entries.length == 1);
      assert(serverCart.entries.length == 1);
      let appCartEntry = appCart.entries[0];
      let serverCartEntry = serverCart.entries[0];
      assert(appCartEntry.sku == "ABCD");
      assert(serverCartEntry.sku == "ABCD");
      assert(appCartEntry.count == 8);
      assert(serverCartEntry.count == 8);
    }

## Cart Delta

A shopping cart delta contains all the information needed to:

- create new entries in a shopping cart
- delete entries
- add price&availability to existing entries
- distinguish later changes from newer ones when multiple entries arrive

Its structure mirrors closely that of *Cart*.

    @json
    export class CartDelta(
      public entryDeltas: List<CartEntryDelta>,
      public postalCode: String | Null,
      public asOf: SequenceMark,
    ) {}

## Cart Entry Deltas

A shopping cart entry delta mirrors the structure of a *CartEntry*
but fields may be *null* to indicate no change.

    @json
    export class CartEntryDelta(
      public sku: String,
      public count: Int | Null,
      public stocked: StockedStatus | Null,
      public asOf: SequenceMark,
    ) {}

## Merging

The *mergeCart* operator computes a shopping cart from a pre-existing shopping
cart and a delta.

    export let mergeCart(
      base: Cart,
      delta: CartDelta,
      asOf: SequenceMark,
    ): Cart {
      // Unroll the entryDeltas into a map.
      // We'll sync them with the entries in one pass, and then any
      // that don't share a SKU with an existing entry get turned into
      // new entries in a later pass.
      let entryDeltasBySku = new MapBuilder<String, CartEntryDelta | Null>();
      for (let entryDelta of delta.entryDeltas) {
        let sku = entryDelta.sku;
        let old = entryDeltasBySku.getOr(sku, null);
        if (old == null || entryDelta.asOf > old.asOf) {
          entryDeltasBySku[sku] = entryDelta;
        }
      }

      let entries = new ListBuilder<CartEntry>();
      for (let entry of base.entries) {
        let sku = entry.sku;
        let entryDelta: CartEntryDelta | Null = entryDeltasBySku.getOr(sku, null);
        var mergedEntry = entry;
        if (entryDelta != null) {
          entryDeltasBySku[sku] = null;  // consume
          mergedEntry = mergeCartEntry(entry, entryDelta);
        }

        entries.add(mergedEntry);
      }

      for (let entryDelta of entryDeltasBySku.values.toList()) {
        if (entryDelta != null) {
          let sku = entryDelta.sku;
          let asOf = entryDelta.asOf;

          var count = 0;
          let dCount = entryDelta.count;
          if (dCount != null) {
            count = dCount;
          }

          let dStocked = entryDelta.stocked;
          let stocked = if (dStocked == null) {
            new UnknownStockedStatus()
          } else {
            dStocked
          };

          entries.add(new CartEntry(sku, count, stocked, asOf));
        }
      }

      var postalCode = base.postalCode;
      if (delta.asOf >= base.asOf) {
        let dPostalCode = delta.postalCode;
        if (dPostalCode != null) {
          postalCode = dPostalCode;
        }
      }
      ({ entries: entries.toList(), postalCode, asOf })
    }

    let mergeCartEntry(entry: CartEntry, delta: CartEntryDelta): CartEntry {
      if (delta.asOf < entry.asOf) {
        return entry;
      }
      let sku = entry.sku;
      var count = entry.count;
      var stocked: StockedStatus = entry.stocked;
      let asOf: SequenceMark = delta.asOf;

      let dCount = delta.count;
      let dStocked = delta.stocked;
      if (dCount != null) {
        if (dCount > count) {
          // If the count increases, then any availability info is suspect.
          // We might have known that 3 were available, but if they're
          // now requesting 4, we need to recheck availability.
          stocked = new UnknownStockedStatus();
        }
        count = dCount;
      }
      if (dStocked != null) {
        stocked = dStocked;
      }
      if (stocked.is<Stocked>() && stocked.as<Stocked>().asOf < asOf) {
        // Discard out of date stocking info
        stocked = new UnknownStockedStatus();
      }

      new CartEntry(sku, count, stocked, asOf)
    }

## Difference between two carts

*diffCart* produces a delta from two carts.
The argument order is the same as subtraction:

- `cartDelta = newCart - oldCart`
- `let cartDelta = diffCart(newCart, oldCart, asOf);`

The *asOf* argument is used to determine which entries are unknown
by the receiver of the *delta*.

    export let diffCart(
      newCart: Cart,
      oldCart: Cart,
      asOf: SequenceMark,
    ): CartDelta {
      let entryDeltas = new ListBuilder<CartEntryDelta>();
      var postalCode: String | Null = newCart.postalCode;
      if (postalCode == oldCart.postalCode) {
        postalCode = null; // No change
      }

      // Collect entries by SKU from the old cart.
      // First, we'll make sure there's an entry for any that changed or that are new in newCart.
      // Second, we'll create a zero count entry tombstone for any in oldCart that are missing in newCart.
      let entriesBySku = new MapBuilder<String, CartEntry | Null>();
      for (let oldEntry of oldCart.entries) {
        let sku = oldEntry.sku;
        let old = entriesBySku.getOr(sku, null);
        if (old == null || oldEntry.asOf > old.asOf) {
          entriesBySku[sku] = oldEntry;
        }
      }

      // First, ensuring entries mentioned in newCart are accounted for.
      for (let newEntry of newCart.entries) {
        let sku = newEntry.sku;
        let newCount = newEntry.count;
        let newStocked = newEntry.stocked;
        let newAsOf = newEntry.asOf;

        let oldEntry = entriesBySku.getOr(sku, null);
        if (oldEntry == null) {
          entryDeltas.add(new CartEntryDelta(sku, newCount, newStocked, newAsOf));
        } else {
          entriesBySku[sku] = null; // Consume so not seen by second loop.

          let oldCount = oldEntry.count;
          let oldStocked = oldEntry.stocked;
          let oldAsOf = oldEntry.asOf;

          let countChanged = newCount != oldCount;
          let stockedChanged = !newStocked.equals(oldStocked);
          let anyChanged = countChanged || stockedChanged;
          let newToMe = newAsOf > asOf;

          if (newToMe || anyChanged) {
            let dCount   = newToMe || countChanged   ? newCount   : null;
            let dStocked = newToMe || stockedChanged ? newStocked : null;
            entryDeltas.add(new CartEntryDelta(sku, dCount, dStocked, newAsOf));
          }
        }
      }

      // Second, add tombstones for anything that wasn't consumed from entriesBySku in the previous loop.
      for (let oldEntry of entriesBySku.values.toList()) {
        if (oldEntry != null) {
          // Add a tombstone
          entryDeltas.add(new CartEntryDelta(oldEntry.sku, 0, new UnknownStockedStatus(), asOf));
        }
      }

      ({ entryDeltas: entryDeltas.toList(), postalCode, asOf })
    }
