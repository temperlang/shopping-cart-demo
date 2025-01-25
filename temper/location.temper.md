# Location

A shopping cart location answers questions about:

- does the shopper want to order online for delivery to a particular
  zipcode?
- or does the shopper want to pick up in-store, if so which store?

For demo purposes, we just store a postal code: US zip code or other string identifier.

    @json
    export class Location(
      public postalCode: String | Null,
      public marks: Marks,
    ) extends Resolvable {

Given a location, we can figure out which currency to use for transactions.

      public get currencyCode(): String {
        let postalCode = this.postalCode;
        if (postalCode == null) {
          unknownCurrencyData.code
        } else {
          // We assume any cleanup and coercion is done client side.
          // US zip codes have two forms:
          //   [0-9]{5}                      // Five digits
          //   [0-9]{5}[+][0-9]{4}           // Five'+'four digits
          // Canadian postal codes have one form:
          //   [0-9a-zA-Z]{3} [0-9a-zA-Z]{3} // Two groups of 3 alphanums
          let length = postalCode.countBetween(String.begin, postalCode.end);
          // TODO: use regexen
          when (length) {
            5, 10 -> "USD";
            7 -> "CAD";
            else -> unknownCurrencyData.code;
          }
        }
      }

      public toString(): String {
        postalCode ?? "unknown"
      }

      public withMarks(newMarks: Marks): Location {
        ({ postalCode, marks: newMarks })
      }
    }

In a real system, the stocking status would also refer to the actual
store inventory for in-store pickup.

    test('currency code for us zip code') {
      let marks = { cMark: 0 };
      assert(({ postalCode: '80121', marks }).currencyCode == 'USD');
      assert(({ postalCode: '80121-1234', marks }).currencyCode == 'USD');
    }

    test('currency code for canadian address') {
      let marks = { cMark: 0 };
      assert(({ postalCode: 'K1A 0A9', marks }).currencyCode == 'CAD');
    }
