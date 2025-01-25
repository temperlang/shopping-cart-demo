# Pings

Servers and clients need to occasionally trade information about a cart.

A ping request includes:

- the sequence mark as of which the client has changes
- any pending changes initiated by the client or the empty delta

A ping response includes:

- a delta including changes since the sequence mark in the request
- the sequence mark as of which the delta is up-to-date for inclusion
  in subsequent ping requests

Since both the request and response include a sequence mark and a cart
delta, we just need one class.

    @json export class Ping(
      public cartDelta: CartDelta,
      public sMark: ServerMark,
    ) {}

