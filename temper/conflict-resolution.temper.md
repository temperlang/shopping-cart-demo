# Resolving conflicts

When two or more edits are made to the same piece of user data, perhaps one on a smartphone and one on the web, the server may need to:

1. resolve the conflict by deciding on a final state, and
2. notify each device about the final, canonical state so they can update their display or publish alerts.

## Server Marks

A server mark is a canonical identifier for a change.  Since it is assigned by a single authority, it's a reliable basis for questions like:

> I have an up-to-date view of the object at *ServerMark* 123, what has changed since that?
> What is the new *ServerMark* that I am up-to-date with?

If a resolvable object's server mark is greater than the client's known server mark then the client's view may be stale.

A server mark is assigned after a change has been received by the server, and it has been found to not be superseded by a previously received change, then the server (or a database) increments a counter and assigns a server mark.
A monotonic timestamp might work, but is not ideal. In scenarios where multiple instances of the same server software might be running on different physical machines, clock skew could lead to failure to send updates to clients.

    export let ServerMark = Int;

A null server mark means that the server has not yet assigned a number for this change.

If a client needs to initialize a *ServerMark* field to indicate "the view has not been synchronized with the server's yet"; servers must not assign negative server marks, but should, when receiving one, respond with a full update.

## Client Marks

A client mark may be created by any client, without consulting the server, even when offline.  They are comparable across clients; one client's mark may be compared to another to answer questions like:

> Which of these conflicting change *probably* happened later?

Because of *clock skew*, any answer to happens-after on disconnected devices is probabalistic, and not solvable in an adversarial scenario.

In production, a monotonic timestamp (an approximation of milliseconds since epoch), is a great choice.
Test code can just use a counter.

Client marks are used in values and in deltas to manage merge conflicts.
Order is important; typically, an entry with a greater client mark has
precedence over a lesser one.

    export let ClientMark = Int;

*Marks* bundle those two pieces of information together.

    @json
    export class Marks(
      public cMark: ClientMark,
      public sMark: ServerMark? = null,
    ) {
      public withSMark(newSMark: ServerMark?): Marks {
        ({ cMark, sMark: newSMark })
      }
    }

Each sub-type of the *Resolvable* interface has the marks needed to resolve conflicts and to make sure clients eventually receive updates to their local views:

    export interface Resolvable {
      public marks: Marks;
    }

## Resolving changes

Each of the questions above involving marks can be answered with these helper functions.

*Later* answers the question of which to use by preferring later client marks.

    export let later<R extends Resolvable>(
      current: R,
      received: R?,
    ): R {
      if (received != null) {
        let receivedCMark = received.marks.cMark;
        let currentCMark = current.marks.cMark;
        if (
          receivedCMark > currentCMark ||
          (receivedCMark == currentCMark &&
           // Prefer incorporating server-stamped versions into
           // existing entries.
           received.marks.sMark != null && current.marks.sMark == null)
        ) {
          received
        } else {
          current
        }
      } else {
        current
      }
    }

*LaterOrNull* is like *later* but doesn't require that you have a thing to start with.

    export let laterOrNull<R extends Resolvable>(
      current: R?,
      received: R?,
    ): R? {
      if (current != null) {
        later<R>(current, received)
      } else {
        received
      }
    }

A client needs an update for a *Resolvable* object if its latest *ServerMark* is less than the one stored with the object.

    let clientNeedsUpdate(
      current: Resolvable?,
      clientHas: ServerMark,
    ): Boolean {
      current != null && clientHas < current.marks.sMark
    }

## An example change resolution

For example, Alice uses both her tablet and her laptop with her account with
*example.com*.

Sometimes her tablet is disconnected from the network, but the app lets her
edit locally and saves changes until she reconnects.

Alice performs the following changes:

1. At 10 AM Alice uses her tablet to make a change.
   The tablet assigns *SequenceMark* `10:00`.
2. At 10:30 AM Alice has an idea and uses her laptop via a web interface to
   make a change and the web client uses *SequenceMark* `10:30`.
3. Alice logs back into the tablet at 11 AM.

The tablet could be offline during 1.

| Timeline A | Timeline B


## A resolution example

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
      var now: ClientMark = 1059; // Server time

      // User creates a cart via a mobile app
      var appCart = {
        entries: [],
        loc: { postalCode: "90210", marks: { cMark: now } }
      };
      var appHas: ServerMark = -1;

      // The server has a view of its cart, and its SMark counter.
      var sMark = 0;
      var serverCart = { entries: [], loc: { postalCode: null, marks: { cMark: 0, sMark } } };

      // An app syncs to the server updating the cart for that user account.
      let newCartRequest = { entryDeltas: [], loc: { postalCode: "90210", marks: { cMark: now, sMark: 0 } } };
      serverCart = mergeCart(serverCart, newCartRequest, ++sMark);

      // 1. User adds something locally.
      now = 1100;
      let appAdditions = {
        entryDeltas: [new CartEntryDelta("ABCD", 10, null, { cMark: now })],
        loc: null,
      };
      appCart = mergeCart(appCart, appAdditions);
      // 2. Not merged into the server yet.

      // 3. CRM makes additions and sends those to the server
      now = 1110;
      var crmAdditions = {
        entryDeltas: [new CartEntryDelta("ABCD", 8, null, { cMark: now })],
        loc: null,
      };
      // 4. Server merged CRM changes
      serverCart = mergeCart(serverCart, crmAdditions, ++sMark);

      // 5. App attempts to merge to server.
      now = 1115;
      let serverCartBeforeAppMerge = serverCart;
      serverCart = mergeCart(serverCart, appAdditions, ++sMark);
      let updatesForApp = diffCart(serverCart, serverCartBeforeAppMerge, appHas);
      // App receives updates from server.
      appCart = mergeCart(appCart, updatesForApp);

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
