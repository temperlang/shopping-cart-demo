# Prices

Support for [ISO 4217] which defines currency codes for representing monies.

## Price type

A price is an amount of an [ISO 4217] currency. For example, 2000 Japanese Yen
is represented as `{ "currencyCode": "JPY", "amount": 2000 }`.
The amount may be positive, zero, or negative.

    @json
    export class Price(

currencyCode is a string identifying the currency.
For example, "USD" represents US dollars.
See also [the active codes list].

      public currencyCode: String,

Amount is the amount of the currency.
ISO 4217 currency entries include a *minorUnit* value, the number of digits
after the decimal point. That many digits of amount are after the decimal points.

For example, US dollars (USD) have a *minorUnit* of 2 because the smallest
currency unit is (10⁻²), one hundredth, of a dollar: the cent.
In USD, $10.00 has an amount of 1000.

      public amount: Int,

    ) {
      public get currencyData(): CurrencyData {
        currencyDataTable.getOr(currencyCode, unknownCurrencyData)
      }

      public equals(other: Price): Boolean {
        amount == other.amount && currencyCode == other.currencyCode
      }

We can format prices to strings.

      public toString(): String {
        let cd = currencyData;

        let digits = amount.toString(10);
        // If we need 2 decimal digits, pad "1" to "001" so that we
        // can split easily "001" to "0.01".
        let paddedDigits = padTo(digits, cd.minorUnit + 1);
        var splitPoint = paddedDigits.end;
        for (var n = cd.minorUnit; n != 0; n -= 1) {
          splitPoint = paddedDigits.prev(splitPoint);
        }

        let sb = new StringBuilder();
        if (cd.code == unknownCurrencyData.code) {
          sb.append(currencyCode);
        }
        sb.append(cd.textPrefix);
        sb.appendBetween(paddedDigits, String.begin, splitPoint);
        if (splitPoint < paddedDigits.end) {
          sb.append(cd.decimalPoint);
          sb.appendBetween(paddedDigits, splitPoint, paddedDigits.end);
        }
        sb.append(cd.textSuffix);
        sb.toString()
      }
    }

Some examples of price formatting.

    test("price formatting") {
      assert({ currencyCode: "USD", amount:    0 }.toString() == "US$0.00");
      // 1 with US dollars means 1 cent because USD below has 2 digits after the decimal point.
      assert({ currencyCode: "USD", amount:    1 }.toString() == "US$0.01");
      assert({ currencyCode: "USD", amount:   10 }.toString() == "US$0.10");
      assert({ currencyCode: "USD", amount: 1000 }.toString() == "US$10.00");
      assert({ currencyCode: "USD", amount: 1234 }.toString() == "US$12.34");
      // Negative amounts
      assert({ currencyCode: "USD", amount: -234 }.toString() == "US$-2.34");
      assert({ currencyCode: "USD", amount:   -5 }.toString() == "US$-0.05");
      // Non US-centric currency.
      assert({ currencyCode: "CAD", amount:  100 }.toString() == "CA$1.00");
      // An unknown currency includes the code for debugging purposes.
      // ¤ is Unicode's generic currency symbol.
      assert({ currencyCode: "WTF", amount:   12 }.toString() == "WTF¤12");
    }

The `@json` notation means we can send prices over the network.

    let { JsonTextProducer, NullInterchangeContext, parseJson } = import("std/json");

    test("price json encoding") {
      let price = { currencyCode: "USD", amount: 1234 };
      let json = '{"currencyCode":"USD","amount":1234}';

      let t = new JsonTextProducer();
      Price.jsonAdapter().encodeToJson(price, t);

      assert(t.toJsonString() == json);
    }

    test("price json decoding") {
      let jsonSyntaxTree = parseJson('{ "amount": 100, "currencyCode": "AUD" }');
      let price = Price.jsonAdapter().decodeFromJson(
        jsonSyntaxTree,
        NullInterchangeContext.instance
      );
      assert(price.currencyCode == "AUD");
      assert(price.amount == 100);
    }


## Currency definitions

Currency data includes information about the kind of currency in a currency list.

    export class CurrencyData(
      /** The ISO 4217 code */
      public code: String,
      /** The number of digits in an amount that should appear after the decimal point */
      public minorUnit: Int,
      /** A string that precedes a formatted price that uses this currency */
      public textPrefix: String,
      /** A string that follows a formatted price that uses this currency */
      public textSuffix: String,
      /** A default decimal symbol for the currency when a number formatting locale is unknown */
      public decimalPoint: String,
    ) {}

A placeholder for an unknown currency code.

    export let unknownCurrencyData = new CurrencyData(
      "???",
      0,
      "¤", // generic currency sign
      "",
      ".",
    );

The data table relates currency codes to data about that code.
Currently, this only contains a few for demo purposes.
TODO: Fully populate these from ISO 4217:2015 at
https://www.iso.org/iso-4217-currency-codes.html and ammendments at
https://www.six-group.com/en/products-services/financial-information/data-standards.html

    export let currencyDataTable: Map<String, CurrencyData> = do {
      let m = new MapBuilder<String, CurrencyData>();
      m["USD"] = {
        code: "USD",
        minorUnit: 2,
        textPrefix: "US$",
        textSuffix: "",
        decimalPoint: ".",
      };
      m["CAD"] = {
        code: "CAD",
        minorUnit: 2,
        textPrefix: "CA$",
        textSuffix: "",
        decimalPoint: ".",
      };
      m["JPY"] = {
        code: "JPY",
        minorUnit: 0,
        textPrefix: "¥",
        textSuffix: "",
        decimalPoint: ".",
      };
      m.toMap()
    };

    let padTo(intDecimalString: String, minDigits: Int): String {
      let end = intDecimalString.end;

      // Skip over any + or - sign.
      var afterSign = String.begin;
      if (afterSign < end) {
        let c = intDecimalString[afterSign];
        if (c == char'-' || c == char'+') {
          afterSign = intDecimalString.next(afterSign);
        }
      }

      // Fast path for when we have enough.
      if (intDecimalString.hasAtLeast(afterSign, end, minDigits)) {
        return intDecimalString;
      }

      let sb = new StringBuilder();
      // Copy over any sign
      sb.appendBetween(intDecimalString, String.begin, afterSign);
      // Pad on the left with zeroes
      var nDigits = intDecimalString.countBetween(afterSign, end);
      while (nDigits < minDigits) {
        sb.append("0");
        nDigits += 1;
      }
      // Copy over the rest.
      sb.appendBetween(intDecimalString, afterSign, end);
      sb.toString()
    }

[ISO 4217]: https://en.wikipedia.org/wiki/ISO_4217
[active codes list]: https://en.wikipedia.org/wiki/ISO_4217#Active_codes_%28list_one%29
