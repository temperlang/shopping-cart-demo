import * as cd from "cart-demo/index.js";
import { parseJson } from "@temperlang/std/json";

console.log("Loaded js");

let cart = new cd.Cart([], null, 0);

export function updateCart(cartDelta) {
  console.log("In initCart got `" + cartDelta + "`");
  if (cartDelta instanceof cd.CartDelta) {
    // Already got it.
  } else {
    // Parse JSON
    let jsonTree = parseJson(cartDelta);
    console.log('jsonTree', jsonTree);
    cartDelta = cd.CartDelta.jsonAdapter().decodeFromJson(jsonTree);
    console.log('cartDelta', cartDelta);
  }
  let newCart = cd.mergeCart(cart, cartDelta, Date.now() | 0);
  console.log('newCart', newCart);
  // Store the new cart.
  cart = newCart;
};
