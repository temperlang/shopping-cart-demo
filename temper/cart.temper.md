# A minimal shopping cart

## A shopping cart type

A shopping cart has a list of entries: counts of products and information like prices.

    export class Cart(
      public entries: List<CartEntry>,

The postal code lets the server pick a currency for purchases.
It can be inferred from the delivery address.

TODO: flesh this out with a purchase sum-type representing: *OnlinePurchase(postalCode)* or *InStorePurchase(storeId)* or *UnknownPurchase*.

      public postalCode: String | Null,
      public asOf: SequenceMark,
    ) {

If, for all entries, we know the unit-price, and those prices are in the same currency, we can compute a total.

      public get totalOrNull(): Price | Null {
        if (entries.isEmpty) { return null }
        let stocked0 = entries[0].stocked;
        if (stocked0 == null) { return null }
        let currencyCode = stocked0.as<Stocked>().price.currencyCode;

        var totalAmount = 0;
        for (let entry of entries) {
          if (entry.count != 0) {
            let stocked = entry.stocked;
            when (stocked) {
              is UnknownStockedStatus -> return null;
              is Stocked -> if (stocked.asOf < entry.asOf) {
                return null
              } else {
                let price = stocked.price;
                // We can't add prices with different currencies.
                if (price.currencyCode != currencyCode) { return null }

                totalAmount += price.amount * entry.count;
              };
            }
          }
        }

        return ({ currencyCode, amount: totalAmount })
      }

There are a number of problems that might affect purchasing the items in a cart.  The problem list allows presenting
problems to people, and also getting a programmatic status that may be used to enable/disable UI elements.

Turning a shopping cart into a purchase order involves extra server side checks, so these are tentative.

      public get problems(): List<CartProblem> {
        // Preserving zero count entries in the list helps,
        // like tombstones, to better resolve merge conflicts.
        // But we don't care about entries with a count of zero.
        let entries = this.entries.filter { (e);; e.count != 0 };

        let problemListBuilder = new ListBuilder<CartProblem>();
        // Trying to purchase an empty cart is likely a user error.
        if (entries.isEmpty) {
          problemListBuilder.add({ message: messageCartIsEmpty, severity: severityTrivial });
        }

        // For each entry is unavailable or not known to be available,
        // that's a problem.
        // If we don't have up-to-date prices, we can't create a
        // purchase order.
        for (let entry of entries) {
          let sku = entry.sku;
          let stocked = entry.stocked;

          if (!stocked.is<Stocked>() || stocked.as<Stocked>().asOf < entry.asOf) {
            problemListBuilder.add({ message: messageNotUpToDate, severity: severityTrivial, sku });
          } else if (stocked.as<Stocked>().price.currencyData == unknownCurrencyData) {
            problemListBuilder.add({ message: messagePriceUnknown, severity: severityTransient, sku });
          } else if (!stocked.as<Stocked>().available) {
            problemListBuilder.add({ message: messageUnavailable, severity: severityBlocking, sku });
          }
          if (entry.count < 0) {
            // This kind of problem should be ruled out by the user interface, but
            // in case this is used with a SaaS gateway, it's good to have a way to bounce
            // bad inputs back to them with some kind of explanation.
            problemListBuilder.add({ message: messageInvalidCount, severity: severityBlocking, sku });
          }
        }

        // Else, if we don't have a total a price, perhaps because some
        // entries use one currency and others use another, that's
        // a problem.
        if (totalOrNull == null) {
          problemListBuilder.add({ message: messageTotalPriceUnknown, severity: severityTransient });
        }

        problemListBuilder.toList()
      }
    }

## Stocked

To turn a a shopping cart into a purchase order, we need reliable price & availability info.

    @json
    export sealed interface StockedStatus {
      public equals(other: StockedStatus): Boolean;
    }

*UnknownStockedStatus* represents a lack of reliable info about the stocking status.

Non-authoritative actors, e.g. clients, should create new entries with *UnknownStocked* stocked.
Servers must not trust *Stocked* info it receives unless this scheme is extended to include a mark of validity like a
cryptographic signature check.

    @json
    export class UnknownStockedStatus extends StockedStatus {
      public equals(other: StockedStatus): Boolean {
        other.is<UnknownStockedStatus>()
      }
    }

*Stocked* collects price & availability from an authority in the context of a source: a store or a warehouse.
A user-interface may use the *asOf* sequence marker, when it corresponds to a time, to determine whether the price and
availability need to be re-requested.

    @json
    export class Stocked(

The price for one amount of the product.

      public price: Price,

Whether the requested amount is available.
If *asOf* is less than the sequence mark of the containing entry, then this may be out of date.
Specifically, availability may not reflect the whole count requested.
Clients should discard *Stocked* when increasing the count of an entry.

      public available: Boolean,

The time as of which the stocked info is valid.

      public asOf: SequenceMark,
    ) extends StockedStatus {
      public equals(other: StockedStatus): Boolean {
        if (!other.is<Stocked>()) { return false }
        let otherStocked = other.as<Stocked>();

        available == otherStocked.available
        && price.equals(otherStocked.price)
      }
    }

## Shopping cart entries

    export class CartEntry(
      public sku: String,
      public count: Int,

The server keeps client images up-to-date with price and availability information.

      public stocked: StockedStatus,
      public asOf: SequenceMark,
    ) {}

## Shopping cart problems

A problem is meant to be displayable to the user and contain enough information so UI code that doesn't know about
message codes can decide whether and how to display messages.

    export class CartProblem(

Each problem has a message string which also keys into the translation bundle.

      public message: String,

Problems have a level of severity which helps UIs decide whether to grab the user's attention.

      public severity: Int,

If the problem applies to a particular cart entry, then a UI can associate it with the entry in the tabular view.

      public sku: String | Null = null,

    ) {}

Here are constants for the message keys.
They are valid English text and key into translation bundles for other locales.
In prod, we would have a regular message ID.

    export let messageCartIsEmpty = "Shopping cart is empty";
    export let messagePriceUnknown = "Price unknown for {{}}";
    export let messageNotUpToDate = "Waiting on price&availability for {{}}";
    export let messageUnavailable = "Product {{}} is not available";
    export let messageInvalidCount = "Please adjust the purchase count for {{}}";
    export let messageTotalPriceUnknown = "Waiting on priceing information";

## Severity levels

| Severity           | Example                        | Description                                                               |
| ---------          | -------                        | -----------                                                               |
| Trivial            | There's nothing in your cart   | Problems that a user will probably resolve in the course of normal usage  |
| Transient          | Waiting to receive final price | Problems that will probably resolve themselves without user action        |
| Blocking           | Product is out of stock        | Problems that may resolve without user action but which will require time |
| Requires Attention | Product no long sold           | Problems that need user attention                                         |

    export let severityTrivial = 0;
    export let severityTransient = 1;
    export let severityBlocking = 2;
    export let severityRequiresAttention = 3;

## Picking a currency

For demo purposes, we look at the postal code to determine the currency prices should be expressed in.
TODO: In a full implementation, we'd want to use a proper address library in conjunction with user profile settings.

    export let pickCurrencyCode(postalCode: String): String {
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
