# Cart demo

    export let version = "0.0.1-beta";
    export let javaGroup = "com.example";
    export let javaPackage = "com.example.cartdemo";

Implements a shopping cart class with merge semantics to show how
sharing a high level definition across nodes in a multi-language
distributed system makes it easier to produce reliable,
well-integrated systems.

Below is an outline of a demo of this work:

- Goals for the demo: convince the audience that sharing type
  definitions makes it easier and faster to produce
  maintainable, tested, and reliable systems.
- Let's start with a type definition: `class Cart`
- Using Temper, a language designed from the ground up to translate
  to all the other languages.
- Shopping carts: everyone knows what they are, but they
  appear in multiple places:

  1. On a web shopping portal written in JS/HTML/CSS
  2. On a mobile app written in Java
  3. On a backend in this case, written in C# that persists
     shopping carts and sessions in a database.
  4. On a call-center CMS that uses Lua to allow a customer service
     representative to edit a user's shopping cart on their behalf.
  5. In some business intelligence data-science code written in Python.

- Modern systems are multi-language systems, often 5-8 PLs at least.
  Show visual from Stackoverflow developer survey.
  Backend devs, client devs, data scientists, each use their preferred
  languages which are the write tools for their jobs.
- But they still have common problems, so can benefit from a way to
  share solutions.

- I'm going to argue:

  - Each language community should use their tools for the parts
    that are about their specialty.
    Use JS for rich Web UIs, use Python for data science.
  - But large parts of the job are not about that.
    Use a sharing language for that.
  - A small infrastructure team can take on common problems
    and support all the other groups.
  - A single-source of truth for core definitions and business
    logic means more feature rich, better tested code.
  - Having all the semantics in one place, means its possible
    to test those semantics without expensive, complex
    end-to-end tests and with less mocking.
  - This allows for rapid prototyping: move some computation
    from one place to another.
  - And rapid re-prototyping.  Non-engineering subject matter
    experts have one point of contact in engineering to hash
    out better, more flexible ways of doing business.

- Let's start with a minimal shopping cart and see what it
  means to share.

- Our minimal shopping cart just has a list of SKU and number
  pairs.

- One thing to note is that this language looks like TypeScript.
  It's not.  It's it's own language with specific semantics
  designed to translate well into many langauges.
  But we want it to be readable by many people, so we patterned
  the surface syntax on JS/TS which are widely recognized.

- I can run `temper build` to translate that into different languages.
- Here's me just creating values for that in the REPL in
  three languages.

- But I need to send that between different nodes. I'll
  add `@json`, without any customization.  That auto-generates
  an adapter.
- And in the REPL, I can see that the JSON adapter provides
  encoders and decoders for strings of JSON.
- Here I've got a simple web client, and JavaScript is
  getting a shopping cart from the server.
- And the server unpacks it.  In normal operation, the
  server would probably store it in the database, but
  in this case it just logs it.
- Even with just this minimal set of code, this solves one
  problem: type guardrails.
  Types are useful within a piece of code.
  But the more micro our micro-services become, the less
  code-quality mechanisms like type checks help.
  But now we've got the same type on both sides.
- Lets make our shopping cart a bit more realistic.
  A store may want to allow for online shopping and
  in-store pickup.  And a price may change between creating
  a shopping cart, and going to checkout, especially if a
  shopping cart is on a mobile device for several days.
- So a server might need to say, client, the authoritative
  price for this product is this, and this row is problematic
  because we don't have enough in inventory.
- It'd be great if a single document could capture these
  subtleties, the why, and define the types and computations.
- Our last file ended with `.temper` but this new version
  ends with `.temper.md`.  It's a semi-literate Markdown
  file that explains why in English prose, alongside
  production code and test code.
- As you can see it explains that a shopping cart is a
  distributed object.  Clients can send a set of changes to
  a server as a *CartDelta*, and a server may set
  back a delta with updates to things like prices.
  And the prose explains that if a customer service
  representative helps a user by editing their shopping
  cart, that those changes need to make it to the client,
  even in some corner case.
  Here's a test of that.  Having all the definitions and
  merge semantics in one place lets us test that scenario
  in a meaningful way without spinning up a mobile app,
  a server, and a giant customer management system.
- Here's another case: a user creates a shopping cart
  in US$ and gets pretty good price info, but then decides
  to pick it up in a store on the other side of the US/Canada
  border, so the server needs to send price updates showing
  Canadian dollars before the checkout can happen.
- Our translation lets us test these corner cases easily.
  The design lets us handle passing in a pricingPolicy
  which, on a real server, has complex business logic based
  on a database of real stores.
  But in a test, we can abstract that out.
- This document explains serves as a single source of truth
  for what a shopping cart is, and how changes from various
  sources are incorporated into an object that can be edited
  from web and mobile and by customer service.
  In Python, our data scientists can simulate user behaviour
  to provide better business intelligence.
- And subject matter experts who understand the laws and
  logistics can work with one engineering team to get their
  ideas in a computable form and work to test corner cases.
- Now imagine, we were building this system from scratch.
  Maybe we want to quickly build a mobile app.
  Users can create a shopping cart, and that lives on their
  phone.  Even offline, they can edit their cart because
  some of the product information is downloaded by the app.
  When they connect to the server, it double checks the
  prices and availability.
- They roll out their mobile app and it's a great success.
  But where's the website?  How is customer support supposed
  to help people?
- Do they have to re-create a lot of logic on the web-platform
  in different languages?
- No, with the core definitions and business logic written
  in Temper, the web team can focus on their specialty:
  producing a good web experience and use the JS/TS translation
  of the libraries.
  The CMS integrators can similarly use a different translation
  of the same shared ideas.
- And the system can evolve rapidly: no need to port changes
  to policies into 3 or 4 different systems as the business
  grows and finds better ways of doing business.
- Micro-services are great if what you want is a stateful,
  asynchronous function, but shared definitions and logic
  are better when TODO TODO TODO.
- The systems we work on are distributed, multi-language systems.
  To produce reliable systems, we need a connective tissue that
  ties all the different language islands into one coherent
  whole.  Temper is a general-purpose programming language for
  producing libraries that can run inside any other languages'
  runtime.
  It lets a small team take on common problems and solve them across a
  whole organization, and support a much larger group of specialists
  working in other languages.
