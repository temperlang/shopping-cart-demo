# Shopping cart demo script

Supporting code for a demo of Temper supporting reliable distributed systems by sharing core type definitions and enabling interchange.

## Audience

Developers, software architects, and engineering leadership.
Especially developer productivity folks.

## Demo goals

Convince the audience that sharing type definitions with Temper makes it easier and faster to produce maintainable, tested, and reliable systems.

## Outline

Today I'd like to talk about Temper, a new programming language *designed from the ground up* to translate to *all* the other programming languages.

Our motivation for this:
there are some hard problems in distributed systems, and they're getting harder.  We need some integrative technology that ties them together. Temper is that; it can make your life easier, and this demo will show you how.

By the way, if you work in an org that maintains a website, some mobile apps, and server software, and has some data scientists, CONGRATS, you're working on a **multi-language distributed system**.

Temper is designed to fill gaps in distributed systems engineering by sharing rich type definitions and business logic across languages.

&rArr; slide 1

What is Temper?

- A new programming language.  A general purpose language meant for industry.
- It's designed from the ground up to translate well to all the other languages
- A team using it can produce libraries that support all the other language communities

&rArr;&rArr; VSCode `price.temper.md`

Here's Visual Studio Code with some Temper code.  You can see that it looks like a markdown description.  The English prose explains what the code does and why.  The indented code sections are Temper code.  It looks like any modern language that uses curly-brackets and semicolons.

This defines a price type.  It's based on an ISO standard. Writing it on Temper we get a library for prices in all the other languages. Later I'll show how sharing types across languages helps us in the demo.

> class Price

Here's a simple class definition.  A price pairs a currency code, defined by the ISO, and an amount.  And this class is pretty simple, but it has some logic: how to format a price value to a human-readable string.  (There's some subtlety there because of minor units which is explained in the prose here.)  Back to the top, this at-JSON notation comes in important later because for the demo we're going to send prices between languages.

----

&rArr; Console

But first let me show you what it means to use this from multiple languages.

This next part is pretty code heavy, so if you're interested in high-level architecture and engineering coordination problems you can skip to the next Youtube chapter mark.

[Switch to the terminal]

> `temper repl -b js -w temper-build-root`

We've designed temper with code exploration in mind and with deep integration into other languages' toolchains.  A REPL is just an interactive playground. `temper repl -b js` means start the JavaScript language's REPL with the JavaScript translations of Temper.  The dash-'w' flag points it at same directory VSCode was using.

It drops me into node so I can write some JavaScript.
This table here shows that it's pre-imported the cartDemo library translation.

> let { Price } = cartDemo
> Price

cartDemo is just a JavaScript module.  Here's the translation of the Price type I showed you earlier.

> Price

Every JS class is its constructor function, but if we dump the first line of its string form.

> `/^.*/.exec(Price.toString())[0]`

You can see that it's just a regular JavaScript class.  We put in a lot of work to avoid any kind of awkward VM embedding.  Temper just translates **its** classes to JS classes, and its functions to regular JS functions.

> let p = new Price('USD', 500);

Let's create a value: five US dollars.

> String\(p\)

When it converts to a string, that invokes that formatting logic from the Temper code.

> p.<kbd>TAB</kbd>
> p.currencyData.<kbd>TAB</kbd>
> p.currencyData.minorUnit

And tab completion works, so a JavaScript developer can use their regular debugging flow to explore and play with these libraries.

> JSON.stringify\(p\)

I pointed out the at-JSON decoration in the Temper code.  JavaScript's builtin JSON conversion idioms just works.
That at-JSON just means "generate functions to encode and decode values" so we can use the same type on two ends of a network pipe and pass values by-copy along it.

----

Let's briefly try the same from a statically typed language.
Here I'm popping up jshell, a Java interactive shell.

> \[Copy imports for std/json and cartDemo]

I import it, and let's create a value.

> Price p = new Price("USD", 750);

String formatting works the same as in JS.

Java doesn't have a short equivalent of "JSON.stringify", but we pretty easily can get the JSON form.

> JsonTextProducer out = new JsonTextProducer();
> Price.jsonAdapter().encodeToJson(p, out);
> String json = out.toJsonString();
> System.err.println(json);

And it's the same.

> var jsonTree = JsonGlobal.parseJson\($6\);

And that same adapter lets us decode.

> Price.jsonAdapter().decodeFromJson(jsonTree, NullInterchangeContext.instance)
> $9 instanceof Price

So we have a simple type definition based on an ISO standard that we wrote once, and we got idiomatic code in multiple languages and a way to send values between different language's runtimes.

----

&rArr; slide 2

Next, I want to show a system using Temper end to end.
To show how centralizing tricky logic lets you cut a lot of Gordion knots in distributed systems.

But first, modern systems are distributed, **really** distributed.  Really polyglot too.

Here's a diagram from Stackoverflow a few years back.
Technologies cluster, and they cluster by language community.  Technologies that are close together are often used together.

&rArr; Point to top left

A lot of data scientists are here.

&rArr; Point to bottom left

A lot of backend devs are here. 

&rArr; Point to bottom center

The web platform is over here.

&rArr; Bottom right

This cluster has a lot of technologies used on mobile devices.

If your organization has data scientists, web developers, app developers, and backend devs, you're conservatively using 5-8 programming languages.

That's great! People get to choose the right tool for the job.
But it also makes it hard to identify common problems and share a common solution.

With Temper, when Python devs and Java devs have a common problem, they can share the solution.

Temper is the right tool for context-agnostic code: core definitions and business logic.  If the code isn't about something intrinsic to data science or about a mobile app or about the web platform, write it in Temper and use it by translation.

----

&rArr; slide 3

At its core, it's a compiled language that translated to many other languages: JavaScript/TypeScript, Java, C#, Lua, Python.  Working on Rust and COBOL.

----

&rArr; slide 4

Temper changes the economics of software development.  A small team using Temper can support all the other language communities.

I mentioned, Gordion knots in distributed systems engineering. Briefly, the problems that I lived as Google grew from a hundred engineers to ten thousand that led me down this path:

- Granularity.  What can we share?  We can share micro-services.  They're great, but micro-services are stateful asynchronous functions.  What if the thing to share is naturally synchronous, or is easily explained in terms of types.
- Transparency.  The more we decompose monoliths into micro-services, the less static analyzers like code checkers, bug finders, can do.  Each micro-service is an island.  Sharing type definitions on both sides of network pipes gives a basis for connecting those islands into an analyzable whole.
- Testability.  As I'll argue later, end-to-end tests are necessary, but having a single source of truth for system semantics makes it much easier to maintain a rich test suite for the edge cases that are too small to warrant spinning up multiple nodes.
- Compositionality.  Stateful functions are cool, but it's easier to solve problems with a composable set of primitives.  Libraries are better for that.
- Migratability.  We should expect well designed systems to outlive some of the tools use to build them.  Migrating systems is a lot easier with the ability to share business logic between an old system and a new while the replacement is being stood up.

----

&rArr; slide 5

Onto the demo. Shopping carts are boring. Boring but people understands them, so I'm going to use them as a running example.

And they have surprisingly complex semantics.
Here are just a few of the sources of complexity.

There are a couple parts of a shopping cart system.

&rArr; slide 6

Going back to our stackoverlow technology clusters.

&rArr; slide 7

But with different language communities.

&rArr; Point center

A server and database, this language cluster here, keeps track of shopping carts for users, and eventually turns shopping carts into purchase orders.

&rArr; Point right and bottom

But for that to happen, Alice, our user, needs to create a shopping cart via a web interface or a mobile app, or both.  These two different language clusters here.

&rArr; Point bottom left

But if Alice runs into a problem, she might call Customer Service.  Often that involves code in an ERP suite, enterprise reource software, written in a different backend language.

Bob, a customer service representative uses that to edit Alice's cart on her behalf.

&rArr; slide 8

This diagram shows such a sequence of interactions between different nodes in our distributed system.

Next I'm going to walk through code written in multiple languages: merge semantics for a shopping cart in Temper, a server in Java, and a web client written in JavaScript.

Normally, when the semantics of shopping carts are spread across code written in 5-8 languages, it's really hard to test interactions like this, much less get this right.

Please keep in mind that Temper lets you centralize logic and share it across nodes in many languages. That's where the benefits in consistency, code size, testability, maintainability, and rapid prototyping come from. 

---

Back to VSCode. Again, feel free to jump to the next chapter if code details don't speak to you.

&rArr; `cart.temper.md`

Here's another file in that same Temper codebase.
We define a cart as a list of entries.  Like 10 bananas.
Each cart has a location which is just a postal code wrapper here.  In a real system, we'd distinguish between in-store pickup and online shopping here.

&rArr; `location.temper.md`

The location type defines logic that lets us figure out which currency to use for prices.

&rArr; `cart.temper.md` CartEntry L162

And each entry has information about whether a product is in stock, and its price.

&rArr; `cart.temper.md` L106 StockedStatus

interface StockedStatus is a sealed interface.  Like Scala and Kotlin that means that it's a discriminated union.  If you don't know what that means, don't worry.

&rArr; L130 Stocked

When we know the price, we use this variant.  Otherwise we use the UnknownStockedStatus variant.

&rArr; `cart-delta.temper.md` L15

Clients make changes to the cart, but the server needs to augment that with the stock info.

A cart delta looks like a cart, but encapsulates changes to an existing cart.  And it's marked at-JSON so clients can send deltas, and the server can send back deltas with pricing information, and any updates the client is not aware of.

You might've noticed that a lot of these values have marks on them.

&rArr; `conflict-resolution.temper.md` L45

Marks are what let the server resolve conflicting edits; what gets us eventual consistency.  For example, if changes are made on a mobile device which is off network, it can save those edits and let the user keep making changes, and then sync when they reconnect.

Client marks allow for resolving conflicts.
And the server needs to be able to answer questions like: what are all the changes since the last time we connected.  The server mark allows for that because it's immune to clock skew by design.

&rArr; L61

Here's a useful generic function that allows for picking the later of two conflict changes.

It's used over here in mergeCart which is an important abstraction.  More about that later.

So we've defined types with important semantics.  A server and multiple clients can collaboratively edit a shared object by passing deltas back and forth.

&rArr; `cart.temper.md` L50

Oh, and the cart also defines logic for "problems."  What would prevent this from being turned into a purchase order?  We've shared a lot of presentation logic too.  We don't need to pass error messages back and forth as JSON.  If you're editing offline on the app, there's enough logic running on device to give an accurate picture.

This kind of protocol design work, designing for eventual consistency among multiple views of the same thing, is the bread and butter of distributed systems engineering.
It's super nice to have the entire semantics specified all in one place.

Let's dive into the Java and JavaScript next.

----

&rArr; console to run maven

I wrote a simple Jetty server in Java.  It's about 350 LOC because the important business logic is all shared even though Java is an older, verbose language.

Before we dive into Java code, let's me just fire that up.
Ok. I'm maven installing my cart demo code.
Oh, you can see here that it's running tests.

Just a quick aside on tests.

&rArr; `conflict-resolution.temper.md` L133

Here's the Temper code again.  This test block specifies code that isn't production code.  Because Temper code can embed in Markdown, you can have a narrative structure: "Here's what we're doing and why."  Then production code, "Here's how it must do what it does".  And interspersed with that you can say: "For example, given this input, this function produces this output".

These tests get translated too.  So this test, for example, got translated to a JUnit test which maven automatically picked up and ran.
Tests tend to be structurally simple and simple to translate, so you get confidence in the Java translation from tests translated into Java.

&rArr; console

Ok.  I'm running the Jetty Server now.

Switching to the browser.
You're probably thinking that's 2005-era ugly, is he **really** using a marquee tag.  I'm a programming languages person with a focus on reliability and security in distributed systems.  I leave site design to the professionals; the bare bones look is intentional.

I can add an entry.

&rArr; aaa in SKU

&rArr; Point at price

The price shows up as unknown.  The server doesn't know what currency because I haven't told it.

Let me put in a zipcode.

&rArr; 08540 in postal code

And if I put in a canadian postal code, it switches.

&rArr; ABC-123 in postal code

Imagine I go offline on my device.
I'm going to just click this checkbox to simulate a network failure because my browser is pointed at 127 dot 0 dot 0 dot 1 which is always reachable.

I'm going to make some more edits.


If I open my shopping cart in another tab, I get the last version the server knows about.  I'll make some edits here.

Then I go back to the one that's offline, I'll change a count.

And I go back online.  The next time this client pings the server it sends its local edits.

I can see the changes from the other client, and mine merged based on last-wins resolution rules.

And if I switch to the other browser, we can see that the later count clobbered the earlier, but both now have the same view.  We've reached eventual consistency; both clients *and* the server agree on what's in the cart with the server being authoritative on stocking info.

Let's see how the Java and JS code heavily rely on libraries translated from Temper to get all that to work.

----

&rArr; emacs with server loaded

So how does this work.  Let's dive into the Java and JavaScript.
The Java is around 350 LOC counting import statements, comments, and blank lines.
The JavaScript is almost 400 LOC.  I didn't use any frameworks, so DOM edits take some code.

&rArr; Java doctype

First, the Server has some paths for serving static files.

&rArr; L53

Here's where we generate the HTML for the client.

&rArr; Point towards top: `diffCart`

I'm seeding it with JSON.  I use the Temper library to subtract empty cart from the server's current cart to get a delta which I can turn into JSON.

&rArr; Point towards `cartJson = `

And I seed the client with a server mark.  So the client can request changes since its last update.

&rArr; L128 Java Ping decode

And when the client sends something to the server it goes through here.
A Ping is just a type that bundles together a cart delta with a server mark.
The client says "make these changes, and give me any updates since last time."
The server responds with "these are the updates you requested and now you're up to date with this mark."

A quick aside about JSON.  This is type directed conversion.  You can see here that I'm saying Ping dot jsonAdapter.  From a type, I can get a converter that knows how to encode and decode values of that type.  For a generic type you also need to pass adapters for relevant type parameters.
This lets us keep messages small: no putting verbose class names inside every JSON record.  We can encode dates to 8-digit strings for example since when you're decoding a date, the decoder knows, a priori, what it is; it doesn't need to infer types from JSON structure.

This lets us avoid semantic tarpits like reflection, and means that any code analyzers are going to see what's going on instead of it being hidden in magic code.

> Scroll down to synchronized block L143

So the client can send changes which get merged into the server's authoritative view by calling the Temper mergeCart function.

> point at arguments to mergeCart

And here, the server is passing information to update any stocking info, and to mark any new objects with a server mark so it can make sure other clients get those as updates.

> next block diffCart

The server then diffs the new and old carts in the context of the clients last known s-Mark.  That gets the updates the client needs.

> 3 lines later

We use the same Ping dot JSON adapter to decode the request and encode the response.

----

Over to the JavaScript code.

&rArr; JS `let cart =` L1

The JavaScript has a view of the cart defined here.
And it has the last serverMark it knows about.

&rArr; JS applyCartDelta L20

The generated HTML calls into this function that synchronizes those two pieces of state, and schedules UI updates.

&rArr; JS applyUserChange L228

The buttons all go through these handlers which just create cart deltas, remember that Temper type definition, and add them to the accumulated delta: all the changes that have not yet been accepted by the server.

&rArr; point to `.plus`

Here's where we use a Temper function to accumulate deltas.

&rArr; JS scheduleSend L286

And here's where we send changes to the Server.

&rArr; JS cartDemo.Ping.jsonAdapter

This code packs the network messages.

This is an important point.  We've got typed Java receiving and sending Pings.  We've got JavaScript doing the same.  We've extended type guard rails across the network gap.
That's useful for code health and something that hopefully static analyzers will be able to automatically take advantage of in the future.

Oh, and I'm writing JavaScript here, not TypeScript.  Force of habit.  But Temper translates into JavaScript with type notation in comments so our libraries interoperate with both.

&rArr; JS minus L342

If a change request is successfully processed by the server, we subtract the changes we sent from the acccumulated local changes here using the Temper `minus` function.  If cart deltas weren't idempotent we'd need to do a bit more work to handle the case where a change is received and processed but the acknowledgement gets lost, but marks could expand to handle that.

&rArr; Bottom of file, JS setInterval

And at the end here, we periodically ping the server.  We just try to send our accumulated changes every 10 seconds.  Often this'll be the empty delta.  But if we're reconnecting after offline edits, it might not be.

----

&rArr; slide 8

I talked about testable systems earlier.

[Sequence diagram]

If I wanted to test an interaction like this, and the business logic was spread across a server and a client, and ERP suite customizations, the only way to test might be end-to-end tests.

1. Fire up the backend server in a test environment
2. Fire up an instance of the CRM server.
3. Instrument a headless browser to fill forms.
4. Interrogate one or more of those to figure out if the result looks right.
5. Pray that all that asynchrony doesn't make the tests too flaky, and that they don't become a maintenance burden.

Some end-to-end tests are necessary, but remember how the server and client code just delegated all the message managment to Temper code.

&rArr; VSCode Temper `conflict-resolution.temper.md` merge conflict resolved L151

Here's an example of testing the message and merge semantics in a little over a page of Temper code.

For critical systems, you will always need some end-to-end tests, but combining a modest end-to-end test suite with a large suite of unit tests that cover semantic corner cases will lead to a more robust, maintainable system.

----

&rArr; slide 9

Going back to the big picture.  To do this without Temper, you'd need a lot more code.  You'd need to write and maintain code like the merge and diff cart functions in multiple languages.

Here's the final tally:

- 1000 LOC in Temper.  About 650 lines of prod code, 350 of test code.
- 350 LOC in Java
- 400 LOC in JavaScript

Imagine there's a mobile app that is the same size as the JS, another 400

These counts are low for real systems because it's bare bones but still comparable.

With Temper, that's 2150 LOC.  If you had to write the Temper equivalent in just 3 languages, its 4150 LOC.  Temper saved over half just initially; business logic adds up.

Perhaps just as significant though are the benefits to maintainers.

Faced with translating all the Java business logic in JavaScript, more often than not you get a **poorly tested**, buggy half-reimplementation of **some** of the original.  And when you need to change the semantics, that requires coordinating changes to multiple disparate, inconsistently tested codebases; that's an engineering management headache.  With Temper, you change once, test once, and push library updates to many.

Having a single source of truth for your distributed system semantics lets you focus effort on producing one, well tested, high quality implementation.

&rArr; slide 10

So here's the spiel:

- Devs should pick the right tool for the job
- The right tool for routing requests in a backend server and formulating responses is probably a language like Java.  But if it isn't about web server stuff, consider Temper.
- The right tool for making HTML responsive is JavaScript, but if it isn't aboutthat, Temper might be better.
- Similarly for mobile devices.
- The right tool for data science is a lang like Python, but data scientists need to simulate business logic, so can benefit from Temper.

All the code about shopping carts: how to define them, incorporate changes into them, calculate the total price, identify problems.
All that is better done in Temper.

---

&rArr; slide 11

And an org that adopts Temper will benefit in a number of ways:

- Small groups of domain experts can figure out what should happen and how.
- Focus specification and testing effort.
- Lower code footprint and maintenance cost.
- A single source of truth for policy code means that domain experts have a single engineering point of contact to try new ways of doing things.  Small changes in business practices often need engineering management to coordinate software changes across teams, but with Temper, more can be done by devs working with domain experts.
- Rapid prototyping is easier when people can focus on specifying what the system needs to do, without first committing to where that code will run.
- Migrating systems from legacy stacks can be easier when you can factor out some business logic or type definitions into Temper, then use it by translation in the legacy system, update it as necessary, and also use it by another translation in the replacement system under development.
- Sharing some display logic helps a lot.  In the demo we shared price localization and problem messages.  Obviously, that is a big help to mobile and web developers; it can be help with email marketing messages, and it's a nice to have in server-side logging.
- Keeping feature parity between web sites and mobile apps becomes a lot easier with more ways to share.

----

&rArr; slide 11

If you're a V.P. or director of engineering, maybe you've experienced problems like this:

Different teams break things for other teams.  Management has to step in and sort out who is responsible for what.  But it never seems to end.

A certain amount of that is normal.
But consider: if at the core, you've got an object that needs to change in some ways and not others.  A small team could focus on nailing that down.  Maybe some formal methods consultants could help.  Solve that problem once, comprehensively.  Then have those other teams use libraries that encapsulate that logic.

Consistent, well tested libraries are what let us developers have nice things.

----

For developers,

I think once you start looking around and thinking, "what do I have to do in this language, and what could I share?", you'll find that's a lot of code.

We've been working on Temper for 5 years now.  A lot of the early work was research into other languages and runtimes, but as I've shown, we've got the bones of a really useful tool, and, frankly, a language that's rough around the edges but fun to work in.

It's still early days and we're looking for early adopters.

Hopefully, if you're excited as I am about solving thorny problems in engineering robust distributed systems, or just about programming language design, you'll get excited about Temper.

If so, drop me a line.  I'd love to talk!

[Contact Info]
