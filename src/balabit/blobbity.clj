;; ## Decoding binary data
;;
;; Most often, when one needs to interface with other software, there
;; is already an established communication protocol, and libraries to
;; faciliate the communication. However, there are times when that is
;; not the case, and while a protocol exists, one needs to write the
;; library himself. To add insult to injury, if this protocol - or
;; file format - happens to be binary, decoding data out of it from
;; within Clojure is not an easy task.
;;
;; There is, of course, [Gloss][1], a powerful library of byte format
;; DSL, but it has its share of problems too: namely that it appears
;; to be designed to consume the whole thing (whatever the thing may
;; be), so working with it iteratively is quite a challenge. This is a
;; major issue when developing a library for a format not well
;; understood: one needs to be able to easily say: "get me this
;; structure, from this position onwards, I don't care what is in
;; there before or after it."
;;
;;  [1]: https://github.com/ztellman/gloss
;;
;; This library was born of frustration, and its primary purpose is to
;; make this kind of development more agile. Of course, that comes
;; with a price: this is strictly for decoding, there is no
;; encoder. With that price paid, the library aims to please the
;; developer, by being very easy to extend programmatically, and by
;; being designed to work iteratively.
;;
;; While this way is not entirely idiomatic, as it does encapsulate -
;; and rely - on state, whenever it is possible, the library aims to
;; provide idiomatic interfaces.

(ns balabit.blobbity
  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012-2013 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Eclipse Public License - v 1.0"
              :url "http://www.eclipse.org/legal/epl-v10.html"}}

  (:use [balabit.blobbity.typecast])
  (:import (java.nio ByteBuffer)))

; We need to pre-declare `decode-blob`, because certain frame
; decoders (`:struct`, in particular) will need to use it.
(declare decode-blob)

;; ## Decoding a single frame
;;
;; Decoding a single frame is the heart of the library, and is
;; accomplished by the `decode-frame` multi-method. The major reason
;; for using a multi-method is extensibility: the library provides
;; decoders for most primitive types, and a few common constructs
;; only. Everything else can be implemented by adding a new method to
;; the multi-method, with the appropriate dispatch key.
;;

(defmulti decode-frame
  "Decode a single frame from a `ByteBuffer` of the specified type.
  Depending on the type, one or more options may be specified.

  Examples:

    (decode-frame buffer :byte) ;=> 42
    (decode-frame buffer :string 5) ;=> \"MAGIC\""

  {:arglists '([buffer type & options])}

  (fn [#^ByteBuffer _ type & _] type))

;; ### Primitive types
;;
;; Primitive types are those that `java.nio.ByteBuffer` has readers
;; for (and their unsigned counterpart). Decoding these all follow the
;; same pattern: we use the Java method to decode the type, coerce it
;; into the desired type, and - optionally - perform a transformation,
;; such as turning a signed integer into unsigned.
;;
;; Due to this common root, this functionality was extracted into a
;; macro, that can be used to define a new method for the
;; `decode-frame` multi-method:
;;

(defmacro defdfm
  "Create and install a frame decoding method for a particular `type`,
  using `getter` to extract the value from a ByteBuffer, `typecast` to
  coerce the result into a given type, and `transform` to bring the
  data to its final shape.

  All parameters except for `type` must be callable functions."

  [type getter typecast transform]

  `(defmethod decode-frame ~type [#^ByteBuffer buffer# ~'_]
     (-> (~getter buffer#)
         (~typecast)
         (~transform))))

;;
;; Armed with the macro, we can implement the decoders for the
;; primitive types:
;;

;; Decoding a signed byte is done via the `get` method of
;; `ByteBuffer`. The result is coerced into a byte, and no
;; transformation is applied.
(defdfm :byte .get byte identity)
;; Decoding an unsigned byte is similar, with the difference being
;; that a `byte->ubyte` transformation gets applied too.
(defdfm :ubyte .get byte byte->ubyte)

;; Decoding a signed 16-bit integer is doen via the `getShort' method
;; of `ByteBuffer`. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int16 .getShort short identity)
;; Decoding an unsigned 16-bit integer is similar, with the difference
;; being that a `short->ushort` transformation gets applied too.
(defdfm :uint16 .getShort short short->ushort)

;; Decoding a signed 32-bit integer is doen via the `getInt' method of
;; `ByteBuffer`. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int32 .getInt int identity)
;; Decoding an unsigned 32-bit integer is similar, with the difference
;; being that a `int->uint` transformation gets applied too.
(defdfm :uint32 .getInt int int->uint)

;; Decoding a signed 64-bit integer is doen via the `getLong' method
;; of `ByteBuffer`. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int64 .getLong long identity)
;; Decoding an unsigned 64-bit integer is similar, with the difference
;; being that a `long->ulong` transformation gets applied too.
(defdfm :uint64 .getLong long long->ulong)

;; ### Skipping & slicing
;;
;; First, we implement skipping & slicing, which are not really
;; decoding functions, as they serve a different purpose. Including
;; them, however, serves the purpose of making it that much easier to
;; encapsulate padding and uninteresting binary blobs.
;;

;; Binary files sometimes contain padding, which we do not wish to
;; read at all, just to discard them. The `:skip` method comes in
;; handy in these cases, as it simply positions the buffer a few bytes
;; further.
(defmethod decode-frame :skip
  [#^ByteBuffer buffer _ n]

  (.position buffer (+ (.position buffer) n))
  nil)

;; And for those cases where one needs a subsection of the buffer to
;; do further decoding upon, the `:slice` decoder can be used. Give it
;; a length, and it will spit out a ByteBuffer that starts at the
;; current buffer position, limited to the specified length.
(defmethod decode-frame :slice
  [#^ByteBuffer buffer _ length]

  (let [order (.order buffer)
        #^ByteBuffer blob (-> buffer .slice (.limit length))]
    (decode-frame buffer :skip length)
    (.order blob order)))

;; Similar to `:slice`, the `:array` decoder extracts a subsection of
;; the buffer, but instead of returning a ByteBuffer, it returns a
;; byte array. This is to be used in cases where the result is to be
;; passed onto another Java constructor that expects an array (such as
;; String).
(defmethod decode-frame :array
  [#^ByteBuffer buffer _ length]

  (let [a (byte-array length)]
    (.get buffer a)
    a))

;; Similar to the `:array` decode, `:sequence` extracts multiple
;; elements from the buffer, returning a lazy sequence. The elements
;; are decoded using optional custom arguments, each element with the
;; same parameters.
(defmethod decode-frame :sequence
  [#^ByteBuffer buffer _ & decode-params]

  (lazy-seq
   (when (< (.position buffer) (.limit buffer))
     (cons (apply decode-frame buffer decode-params)
           (apply decode-frame buffer :sequence decode-params)))))

;; ### Compound, but common types
;;
;; Apart from the primitive types, and the padding and slicing
;; decoders, there are a few common constructs the library supports
;; out of the box, such as various kinds of strings, and structures
;; (them themselves built up from other frames `decode-frame` can work
;; with).

;; We have a few kinds of strings: one where we know the length in
;; advance (often used as magic markers); one where we do not know the
;; length, but it is terminated by a delimiter (a C string is a
;; variant of this).
;;
;; These string-y types are the first compound types the library
;; implements.

;; Decoding a string of a specific length is done by reading the
;; required amount of bytes into an array, and turning that back into
;; a string.
(defmethod decode-frame :string
  [#^ByteBuffer buffer _ length]

  (String. #^bytes (decode-frame buffer :array length)))

;; Strings are often stored by having a terminal condition, to easily
;; decode data from these, we have a generic predicate-based string
;; decoder, that'll extract a string up until the predicate becomes
;; true.
(defmethod decode-frame :pred-string
  [#^ByteBuffer buffer _ pred?]

  (-> (take-while (complement pred?)
                  (decode-frame buffer :sequence :byte))
      byte-array
      String.))

;; When strings can end at any char within another array, they're
;; delimited by those chars. With the `:delimited-string` method,
;; these can be easily decoded. For example, a double-colon terminated
;; string can be read as:
;;
;;     (decode-frame buffer :delimited-string [\:])
(defmethod decode-frame :delimited-string
  [#^ByteBuffer buffer _ delimiters]

  (let [byte-delimiters (map #(byte (int %1)) delimiters)]
    (decode-frame buffer :pred-string (fn [c] (some #{c} byte-delimiters)))))

;; Decoding NULL-terminated C-like strings is slightly more
;; complicated, as the buffer must be read until the first NULL-byte
;; only, but we have no indication of its length.
;;
;; The result shall be the string itself, without the trailing
;; NULL-byte.
(defmethod decode-frame :c-string
  [#^ByteBuffer buffer _]

  (decode-frame buffer :pred-string zero?))

;; However, it is not only strings we want to handle here, but structs
;; too! Structs that are built up from other frames. We'll see later
;; how these are described when we get to the `decode-blob`
;; function. Nevertheless, structs allows us to embed frames within
;; other frames, and give the result structure.
(defmethod decode-frame :struct
  [#^ByteBuffer buffer _ struct-spec]

  (decode-blob buffer struct-spec))

;; ### Wrapping things up
;;
;; In binary formats, it is fairly common to have a length prefix
;; before a given blob of data. This is a generic enough
;; functionality, that we introduce a decoder for this kind of
;; wrapping: One, that in addition to the usual decode-frame arguments
;; takes a `data-type` and a `prefix-type` argument aswell.
;;
;; It first decodes the length, using `prefix-type`, then decodes the
;; data, dispatching on `data-type` and supplying the extracted length
;; as an additional argument.
;;
;; This, of course, assumes that the prefix-type returns something
;; numeric, and that the method we dispatch to for the data takes an
;; additional length argument.
;;
;; For example, to read a string that is prefixed by a 32-bit length,
;; we can do the following with this decoder method:
;;
;;     (decode-frame buffer :prefixed :string :uint32)
(defmethod decode-frame :prefixed
  [#^ByteBuffer buffer _ data-type prefix-type]

  (decode-frame buffer data-type
                (decode-frame buffer prefix-type)))

;; ----------------------------------------------------------------
;; ## Decoding a set of frames
;;
;; Now that we have the foundations laid out, we can build on top of
;; it: we can build a function to which we can give a C struct-like
;; specification, and it will returns us a map of data. We'll call
;; this function `decode-blob`, but we will have to implement a few
;; helper functions first.
;;
;; The spec we want is flexible: it is a vector of key-value pairs,
;; where the value can be another vector of the type and optional
;; parameters (eg, in case of `:string`, it needs an extra length
;; argument).
;;
;; We want the folling spec to be valid:
;;
;;     [:magic [:string 4]
;;      :tail-offset :uint64]
;;
;; ### Helper functions
;;
;; For this to work, we need a helper function that can decide how to
;; use the value, whether to expect an argument, or just a type.
;;
;; This is `decoder-dispatch`:
;;

(defmulti decoder-dispatch
  "Given an element spec, dispatch it to the appropriate
  `decode-frame` call."

  {:arglists '([buffer type] [buffer [type params]])}

  (fn [#^ByteBuffer _ options]
    (class options)))

;; If the elem-spec is a keyword, pass it on to read-element as-is.
(defmethod decoder-dispatch clojure.lang.Keyword
  [#^ByteBuffer buffer type]
  (decode-frame buffer type))

;; If the elem-spec is not a keyword, then assume it is a [type &
;; options] vector, so destructure it, and pass it onwards. Care is
;; taken to pass all options along, as there can be multiple ones.
(defmethod decoder-dispatch :default
  [#^ByteBuffer buffer [type & options]]

  (apply (partial decode-frame buffer type) options))

;;
;; We also want to allow one to easily skip parts of a binary blob,
;; and skipping does not need, and should not need a dummy key
;; name. Instead, skipping should be inlineable, with `:skip` taking
;; the place of the key, and its argument the place of value:
;;
;;     [:magic [:string 4]
;;      :skip 128]
;;

(defn- skip-or-decode
  "Used by `decode-blob`, this function receives a buffer, a map and a
  key-spec pair, and depending on a few things, decides how to proceed
  with them.

  If the key is `:skip`, then it will skip as many bytes as
  specified (and return the map unchanged), otherwise it will dispatch
  to `decoder-dispatch` to get the value, and assoc it into the result
  if it is not `nil`. If it is, the map will be returned unchanged."

  [buffer m [key elem-spec]]

  (if (= key :skip)
    (do
      (decode-frame buffer :skip elem-spec)
      m)
    (let [v (decoder-dispatch buffer elem-spec)]
      (if v
        (assoc m key v)
        m))))

;;
;; ### Decoding a set of frames
;;

(defn decode-blob
  "Decode multiple frames from a ByteBuffer, according to a
  specification. The specification is a vector of key-value pairs,
  where the values will be used to dispatch `decode-frame` on. If the
  particular type needs extra arguments, then the type itself and the
  extra parameters must be put in a vector.

  Examples:

    (decode-blob buffer [:magic [:string 4]
                         :header-length :uint32])"

  [#^ByteBuffer buffer spec]

  (assert (even? (count spec)))
  (reduce (partial skip-or-decode buffer) {} (partition 2 spec)))

(defn decode-blob-array
  "Decode all frames of the same type from a ByteBuffer. Use this when
  you have a buffer that contains an unspecified number of frames of
  the same type. The optional arguments following the type are passed
  onto the frame decoder verbatim.

  Returns a lazy sequence."

  [#^ByteBuffer buffer type & options]

  (apply decode-frame buffer :sequence type options))
