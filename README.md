# balabit.blobbity

When it comes to picking out bits and pieces from binary data, the
go-to library is most often [Gloss][1] - a great library, but it has
one major downside: it wants to decode the whole buffer,
always. Working iteratively with it is inconvenient at best.

This library is my answer to this need: blobbity is a very simple
library that allows one to write C struct-like specifications, and
extract primitives out of a ByteBuffer, even if there is data left
past what the spec describes. This makes it possible to easily build
up a binary parser iteratively.

 [1]: https://github.com/ztellman/gloss

### Installation

Blobbity is available on Clojars. Add this `dependency` to your
Leiningen `project.clj`:

```clojure
[org.clojars.algernon/balabit.blobbity "0.1.0-SNAPSHOT"]
```

## Usage

Examples:

```clojure
(require '[balabit.blobbity :as blob])
```

To extract information from a buffer that has a 4-byte magic
identifier, followed by a 16-bit header length, and a 32-bit offset,
pointing to the end of the last written record, one can use the
following spec:

```clojure
(blob/decode-blob buffer [:magic [:string 4]
                          :header-len :uint16
                          :tail-offset :uint32])
```

## License

Copyright © 2012 Gergely Nagy <algernon@balabit.hu>

Distributed under the Eclipse Public License, which is also used by Clojure.
