# Merge semantics for shopping carts

A cart delta is a set of changes to a shopping cart.

The merge operation combines a cart and a delta to produce
a merged cart.

Normally, a cart starts life empty, and deltas are merged into it.

A client may merge things locally to quickly update the UI.  In
parallel, it can send the delta to the server which incorporates it
and responds with a delta including authoritative price&availability
info.

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
      public loc: Location?,
    ) {

      public get isEmpty(): Boolean {
        loc == null && entryDeltas.isEmpty
      }

The sum of this and another &Delta; has the entries present in
either, preferring later *CMark* timestamps.
This is useful for accumulating deltas offline on a client for
later submission to a server.

      public let plus(other: CartDelta): CartDelta {
        let bySku = entryDeltasBySkuOf(other);

        for (let entryDelta of entryDeltas) {
          let sku = entryDelta.sku;
          let otherEntryDelta = bySku.getOr(sku, null);

          // Leave the later in the map to be reincorporated into list.
          let laterEntry: CartEntryDelta = later(entryDelta, otherEntryDelta);
          bySku[sku] = laterEntry;
        }

        let mergedEntryDeltas = new ListBuilder<CartEntryDelta>();
        for (let entryDelta of bySku.values().toList()) {
          if (entryDelta != null) {
            mergedEntryDeltas.add(entryDelta);
          }
        }

        let thisLoc = this.loc;
        let otherLoc = other.loc;
        let loc: Location? = laterOrNull<Location>(thisLoc, otherLoc);

        ({
          entryDeltas: mergedEntryDeltas.toList(),
          loc,
        })
      }

The deletion of this and another &Delta; has the entries present in
this that are not newer than corresponding entries in the other.
This is useful for updating a client's local, accumulated delta when
it receives notification that the server successfully processed a
delta it got.

1. Client retrives *local* delta, calls it *sent* and sends it to the server.
2. The user might contininue editing locally, changing *local* to *local'*.
3. Server responds OK.
4. Client sets its local delta to *local'* - *sent* which has
   only those still-outstanding local edits.

So *minus* allows for "still remaining" reasoning.

      public let minus(other: CartDelta): CartDelta {
        let bySku = entryDeltasBySkuOf(other);
        let differenceEntryDeltas = new ListBuilder<CartEntryDelta>();
        for (let entryDelta of entryDeltas) {
          let sku = entryDelta.sku;
          let otherEntryDelta = bySku.getOr(sku, null);
          var inDifference = true;
          if (otherEntryDelta != null) {
            if (otherEntryDelta.marks.cMark >= entryDelta.marks.cMark) {
              inDifference = false;
            }
          }
          if (inDifference) {
            differenceEntryDeltas.add(entryDelta);
          }
        }

        let thisLoc = this.loc;
        let otherLoc = other.loc;
        var loc = thisLoc;
        if (thisLoc != null) {
          if (otherLoc != null) {
            if (otherLoc.marks.cMark >= thisLoc.marks.cMark) {
              loc = null;
            }
          }
        };

        ({
          entryDeltas: differenceEntryDeltas.toList(),
          loc,
        })
      }
    }

When we sum two deltas with different skus we get the union regardless
of order.

    test('sum cart deltas with disjoint skus') {
      let cartDeltaA = {
        entryDeltas: [new CartEntryDelta('A', 1, null, {cMark: 100})],
        loc: null,
      };
      let cartDeltaB = {
        entryDeltas: [new CartEntryDelta('B', 1, null, {cMark: 200})],
        loc: null,
      };

      let aPlusB = cartDeltaA.plus(cartDeltaB);
      let bPlusA = cartDeltaB.plus(cartDeltaA);

      assert(aPlusB.entryDeltas.length == 2);
      assert(bPlusA.entryDeltas.length == 2);

      let aPlusBBySku = entryDeltasBySkuOf(aPlusB);
      assert(aPlusBBySku.has('A'));
      assert(aPlusBBySku.has('B'));

      let bPlusABySku = entryDeltasBySkuOf(bPlusA);
      assert(bPlusABySku.has('A'));
      assert(bPlusABySku.has('B'));
    }

When we sum two deltas with overlap, we get only one.
Counts do **not** sum.

    test('sum cart deltas with overlapping skus') {
      let cartDeltaAB = {
        entryDeltas: [
          new CartEntryDelta('A', 1, null, {cMark: 100}), // disjoint
          new CartEntryDelta('B', 1, null, {cMark: 100}), // overlap
        ],
        loc: null,
      };
      let cartDeltaBC = {
        entryDeltas: [
          new CartEntryDelta('B', 1, null, {cMark: 100}),
          new CartEntryDelta('C', 1, null, {cMark: 100})
        ],
        loc: null,
      };
      let dABPlusBC = cartDeltaAB.plus(cartDeltaBC);
      let dBCPlusAB = cartDeltaBC.plus(cartDeltaAB);
      assert(dABPlusBC.entryDeltas.length == 3);
      assert(dBCPlusAB.entryDeltas.length == 3);
      let dABPlusBCBySku = entryDeltasBySkuOf(dABPlusBC);
      assert(dABPlusBCBySku.has('A'));
      assert(dABPlusBCBySku.has('B'));
      assert(dABPlusBCBySku.has('C'));
      // counts do not sum
      assert(
        ((dABPlusBCBySku['B'] as CartEntryDelta).count ?? -1) == 1
      );

      let dBCPlusABBySku = entryDeltasBySkuOf(dBCPlusAB);
      assert(dBCPlusABBySku.has('A'));
      assert(dBCPlusABBySku.has('B'));
      assert(dBCPlusABBySku.has('C'));
      assert(
        ((dBCPlusABBySku['B'] as CartEntryDelta).count ?? -1) == 1
      );
    }

Subtracting a thing from itself leaves no entry deltas.

    test('subtracting from self such nothing') {
      let cartDelta = {
        entryDeltas: [
          new CartEntryDelta('A', 1, null, {cMark: 100}), // disjoint
          new CartEntryDelta('B', 1, null, {cMark: 100}), // overlap
        ],
        loc: null,
      };
      let shouldBeEmpty = cartDelta.minus(cartDelta);
      assert(shouldBeEmpty.entryDeltas.length == 0);
    }

When we subtract two deltas with disjoint sku sets, we get the left side.

    test('subtracting two deltas with disjoint skus') {
      let cartDeltaA = {
        entryDeltas: [new CartEntryDelta('A', 1, null, {cMark: 100})],
        loc: null,
      };
      let cartDeltaB = {
        entryDeltas: [new CartEntryDelta('B', 1, null, {cMark: 200})],
        loc: null,
      };

      let aMinusB = cartDeltaA.minus(cartDeltaB);
      let bMinusA = cartDeltaB.minus(cartDeltaA);

      assert(aMinusB.entryDeltas.length == 1);
      assert(aMinusB.entryDeltas[0].sku == 'A');

      assert(bMinusA.entryDeltas.length == 1);
      assert(bMinusA.entryDeltas[0].sku == 'B');
    }

When we subtract overlap, a same-age or older entry in the subtrahend
removes from the output.

    test('subtracting two deltas overlap older masks') {
      let cartDeltaAB = {
        entryDeltas: [
          new CartEntryDelta('A', 1, null, {cMark: 100}), // disjoint
          new CartEntryDelta('B', 1, null, {cMark: 100}), // overlap
        ],
        loc: null,
      };
      let cartDeltaBC = {
        entryDeltas: [
          new CartEntryDelta('B', 1, null, {cMark: 200}),
          new CartEntryDelta('C', 1, null, {cMark: 200})
        ],
        loc: null,
      };

      let dABMinusBC = cartDeltaAB.minus(cartDeltaBC);
      let dBCMinusAB = cartDeltaBC.minus(cartDeltaAB);

      // AB is older, so B is subtracted out.
      let skusABMinusBC = entryDeltasBySkuOf(dABMinusBC).keys().toList();
      assert(skusABMinusBC.length == 1);
      assert('A' == skusABMinusBC[0]);

      // BC is younger, so we get both entries.
      let skusBCMinusAB = entryDeltasBySkuOf(dBCMinusAB).keys().toList();
      assert(skusBCMinusAB.length == 2);
      assert('B' == skusBCMinusAB[0]);
      assert('C' == skusBCMinusAB[1]);
    }

## Cart Entry Deltas

A shopping cart entry delta mirrors the structure of a *CartEntry*
but fields may be *null* to indicate no change.

    @json
    export class CartEntryDelta(
      public sku: String,
      public count: Int?,
      public stocked: StockedStatus?,
      public marks: Marks,
    ) extends Resolvable {
      public withMarks(newMarks: Marks): CartEntryDelta {
        ({ class: CartEntryDelta, sku, count, stocked, marks: newMarks })
      }

      public withStocked(newStocked: StockedStatus?): CartEntryDelta {
        ({ class: CartEntryDelta, sku, count, stocked: newStocked, marks })
      }
    }

## Merging

The *mergeCart* operator computes a shopping cart from a pre-existing shopping
cart and a delta.

    export let mergeCart(
      base: Cart,
      delta: CartDelta,

If the server needs to stamp changes with a server mark so it can send updates,
pass the new mark here.

      sMark: ServerMark? = null,

Determines stocked status for the given SKU and count at the given location.
Null if the current context doesn't have access to stocking info.

      stockedForSku: (fn (sku: String, count: Int, loc: Location): StockedStatus)? = null,
    ): Cart {
      // First, figure out the location. If the location has changed
      // and we have access to stocking information, then we can proactively
      // regenerate stocking info for otherwise unchanged entries.
      let deltaLoc = delta.loc
      var deltaLocAdjusted = deltaLoc;
      if (deltaLoc != null) {
        if (sMark != null) {
          deltaLocAdjusted = deltaLoc
            .withMarks(deltaLoc.marks.withSMark(sMark));
        }
      }
      let loc = later(base.loc, deltaLocAdjusted);

      let stockInfoInvalid = loc.currencyCode != base.loc.currencyCode;

      // Unroll the entryDeltas into a map.
      // We'll sync them with the entries in one pass, and then any
      // that don't share a SKU with an existing entry get turned into
      // new entries in a later pass.
      let entryDeltasBySku = entryDeltasBySkuOf(delta);

      let entries = new ListBuilder<CartEntry>();
      for (let entry of base.entries) {
        let sku = entry.sku;
        var mergedEntry = entry;

        let entryDelta: CartEntryDelta? = entryDeltasBySku.getOr(sku, null);
        if (entryDelta != null) {
          entryDeltasBySku[sku] = null;  // consume
          // Fold sMark and stock info into the delta.
          var entryDeltaAdjusted = entryDelta;
          if (sMark != null) {
            entryDeltaAdjusted = entryDeltaAdjusted.withMarks(
              entryDeltaAdjusted.marks.withSMark(sMark)
            );
          }
          mergedEntry = mergeCartEntry(mergedEntry, entryDeltaAdjusted);
        }

        if (stockedForSku != null) {
          let stockedMarks = if (stockInfoInvalid && sMark != null) {
            mergedEntry.marks.withSMark(sMark)
          } else {
            mergedEntry.marks
          };
          mergedEntry = mergedEntry.withStocked(
            stockedForSku(sku, mergedEntry.count, loc)
                .withMarks(stockedMarks)
          );
        }

        entries.add(mergedEntry);
      }

      for (let entryDelta of entryDeltasBySku.values().toList()) {
        if (entryDelta != null) {
          let sku = entryDelta.sku;
          var marks = entryDelta.marks;
          if (sMark != null) {
            marks = marks.withSMark(sMark);
          }

          var count = 0;
          let dCount = entryDelta.count;
          if (dCount != null) {
            count = dCount;
          }

          let dStocked = entryDelta.stocked;
          let stocked: StockedStatus = if (stockedForSku != null) {
            stockedForSku(sku, count, loc).withMarks(marks)
          } else {
            dStocked ?? new UnknownStockedStatus()
          };

          entries.add(new CartEntry(sku, count, stocked, marks));
        }
      }

      ({ entries: entries.toList(), loc })
    }

    let mergeCartEntry(
      entry: CartEntry,
      delta: CartEntryDelta,
    ): CartEntry {
      let sku = entry.sku;
      if (delta.marks.cMark < entry.marks.cMark) {
        return entry;
      } else if (delta.marks.cMark == entry.marks.cMark &&
                 entry.marks.sMark != null && delta.marks.sMark == null) {
        // See later for this
        return entry;
      }

      let count = entry.count;
      let dCount = delta.count ?? count;
      // Here's how we merge the stocked info:
      // - If the count has increased, only trust the delta's stocked info.
      // - Else if one of the stocked has a greater sMark, trust that, because
      //   the pricing info is authoritative on the server.
      // - Else use the existing entry's stocked info.
      var stocked: StockedStatus = new UnknownStockedStatus();
      do {
        let eStocked = entry.stocked;
        let dStocked = delta.stocked;

        // If the count increases, then any availability info is suspect.
        // We might have known that 3 were available, but if they're
        // now requesting 4, we need to recheck availability.
        if (dCount > count) {
          stocked = dStocked ?? new UnknownStockedStatus();
        } else if (eStocked is Stocked) {
          stocked = eStocked;
          if (dStocked is Stocked) {
            let dStockSMark: ServerMark =
              dStocked.marks.sMark ?? -2;
            let eStockSMark: ServerMark =
              eStocked.marks.sMark ?? -2;
            if (dStockSMark > eStockSMark) {
              stocked = dStocked;
            }
          }
        } else if (dStocked is Stocked) {
          stocked = dStocked;
        }
      }

      let marks: Marks = delta.marks;

      new CartEntry(sku, dCount ?? count, stocked, marks)
    }

    // Utility for operations on delta entry lists.
    let entryDeltasBySkuOf(
      delta: CartDelta
    ): MapBuilder<String, CartEntryDelta?> {
      let m = new MapBuilder<String, CartEntryDelta?>();
      for (let entryDelta of delta.entryDeltas) {
        let sku = entryDelta.sku;
        m[sku] = later(entryDelta, m.getOr(sku, null));
      }
      m
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
      has: ServerMark?,
    ): CartDelta {
      let oldLoc = oldCart.loc;
      let newLoc = newCart.loc;
      let locChanged = do {
        let oldCMark = oldLoc.marks.cMark;
        let newCMark = newLoc.marks.cMark;
        newCMark > oldCMark ||
          oldCMark == newCMark && (
            newLoc.postalCode != oldLoc.postalCode ||
            (oldLoc.marks.sMark ?? -1) != (newLoc.marks.sMark ?? -1)
          )
      };
      var loc: Location? = if (locChanged) { newLoc } else { null };

      let entryDeltas = new ListBuilder<CartEntryDelta>();

      // Collect entries by SKU from the old cart.
      // First, we'll make sure there's an entry for any that changed or that are new in newCart.
      // Second, we'll create a zero count entry tombstone for any in oldCart that are missing in newCart.
      let entriesBySku = new MapBuilder<String, CartEntry?>();
      for (let oldEntry of oldCart.entries) {
        let sku = oldEntry.sku;
        let old = entriesBySku.getOr(sku, null);
        if (old == null || oldEntry.marks.cMark > old.marks.cMark) {
          entriesBySku[sku] = oldEntry;
        }
      }

      // First, ensuring entries mentioned in newCart are accounted for.
      for (let newEntry of newCart.entries) {
        let sku = newEntry.sku;
        let newCount = newEntry.count;
        let newStocked = newEntry.stocked;
        let newMarks = newEntry.marks;
        let newSMark = newMarks.sMark;

        let oldEntry = entriesBySku.getOr(sku, null);
        if (oldEntry == null) {
          entryDeltas.add(new CartEntryDelta(sku, newCount, newStocked, newMarks));
        } else {
          entriesBySku[sku] = null; // Consume so not seen by second loop.

          let oldCount = oldEntry.count;
          let oldStocked = oldEntry.stocked;
          let oldMarks = oldEntry.marks;

          let countChanged = newCount != oldCount;
          let stockedChanged = !newStocked.equals(oldStocked);
          let anyChanged = countChanged || stockedChanged;
          let newToMe = has != null && newSMark != null && newSMark > has;

          if (newToMe || anyChanged) {
            let dCount   = if (newToMe || countChanged  ) { newCount   } else { null };
            let dStocked = if (newToMe || stockedChanged) { newStocked } else { null };
            entryDeltas.add(new CartEntryDelta(sku, dCount, dStocked, newMarks));
          }
        }
      }

      // Second, add tombstones for anything that wasn't consumed from entriesBySku in the previous loop.
      for (let oldEntry of entriesBySku.values().toList()) {
        if (oldEntry != null) {
          // Add a tombstone
          entryDeltas.add(new CartEntryDelta(oldEntry.sku, 0, new UnknownStockedStatus(), oldEntry.marks));
        }
      }

      ({ entryDeltas: entryDeltas.toList(), loc })
    }

When an authoritative source of stocking information adds stocked
information, that shows up in the changes.

    test("server merge adds sMark and stock info") {
      let { JsonTextProducer, parseJsonToProducer } = import("std/json");
      var cMark: ClientMark = 1000;
      var sMark: ServerMark = 1;
      // State of the cart on the server initially
      let cartBefore = {
        entries: [],
        loc: { postalCode: null, marks: { cMark: 0, sMark: null } }
      };

      // The delta sent by the client
      let delta = {
        entryDeltas: [
          { class: CartEntryDelta,
            sku: "SKU-123",
            count: 10,
            stocked: new UnknownStockedStatus(),
            marks: { cMark, sMark: null },
          }
        ],
        loc: { postalCode: "90210", marks: { cMark, sMark: null } },
      };

      let sMarkClientHas: ServerMark = 0;

      let cartAfter = mergeCart(cartBefore, delta, sMark) { (sku, count, loc) =>
        ({
          price: { currencyCode: loc.currencyCode, amount: 1000 },
          available: true,
          marks: { cMark: 1001, sMark },
        })
      };

      let updateForClient = diffCart(
        cartAfter,
        cartBefore,
        sMarkClientHas,
      );

      let updateJson = new JsonTextProducer();
      CartDelta.jsonAdapter().encodeToJson(updateForClient, updateJson);
      let wantedJson = new JsonTextProducer();
      parseJsonToProducer(
        """
        "{
        "  "entryDeltas": [
        "    {
        "      "sku": "SKU-123",
        "      "count": 10,
        "      "stocked": {
        "        "price": { "currencyCode": "USD", "amount": 1000 },
        "        "available": true,
        "        "marks": { "sMark": 1, "cMark": 1000 }
        "      },
        "      "marks": { "sMark": 1, "cMark": 1000 }
        "    }
        "  ],
        "  "loc": {
        "    "postalCode": "90210",
        "    "marks": { "sMark": 1, "cMark": 1000 }
        "  }
        "}
        ,
        wantedJson
      );
      assert(updateJson.toJsonString() == wantedJson.toJsonString());
    }

When a client gets back a delta that was like theirs but with stocking
info, their merge will incorporate that into their local store.

    test("stocked status merges into same tstamp entries") {
      let clientCart = {
        entries: [
          { class: CartEntry, sku: "SKU-1", count: 11,
            stocked: new UnknownStockedStatus(),
            marks: { cMark: 1000, sMark: null },
          },
        ],
        loc: { postalCode: "90210", marks: { cMark: 1000, sMark: null } },
      };

      let serverPingback = {
        entryDeltas: [
          { class: CartEntryDelta, sku: "SKU-1", count: 11,
            stocked: {
              available: true,
              price: { currencyCode: "USD", amount: 1250 },
              marks: { cMark: 0, sMark: 123 },
            },
            marks: { cMark: 1000, sMark: 123 },
          },
        ],
        loc: { postalCode: "90210", marks: { cMark: 1000, sMark: 123 } },
      };

      let newClientCart = mergeCart(clientCart, serverPingback);

      assert(newClientCart.entries.length == 1);
      assert((newClientCart.loc.postalCode ?? "??") == "90210");
      assert((newClientCart.loc.marks.sMark ?? -1) == 123);
      assert(newClientCart.entries[0].stocked is Stocked);
      assert((newClientCart.entries[0].marks.sMark ?? -1) == 123);
    }
