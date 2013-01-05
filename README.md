# balabit.blobbity

[![Build Status](https://travis-ci.org/algernon/balabit.blobbity.png?branch=master)](https://travis-ci.org/algernon/balabit.blobbity)

This library was born out of a need to have a simply extensible binary
data decoding library, which can work iteratively. That is all it
does, and hopefully does it well.

### Installation

Blobbity is available on Clojars. Add this `dependency` to your
Leiningen `project.clj`:

```clojure
[com.balabit/balabit.blobbity "0.1.0"]
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

As an example for extending the decoders, to parse a [netstring][2],
we'd only need to introduce a decoder for it:

```clojure
(defmethod blob/decode-frame :netstring
  [#^ByteBuffer buffer _]

  (let [len (Integer/parseInt (blob/decode-frame buffer :delimited-string [\:]))
        netstr (blob/decode-frame buffer :string len)]
    (blob/decode-frame buffer :skip 1)
    netstr))
```

 [2]: http://en.wikipedia.org/wiki/Netstring

For more information, see the [API documentation][3].

 [3]: http://algernon.github.com/balabit.blobbity/

## License

Copyright © 2012-2013 Gergely Nagy <algernon@balabit.hu>

Distributed under the Eclipse Public License, which is also used by Clojure.
