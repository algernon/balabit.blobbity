(ns balabit.blobbity
  "Binary blob decoding functions.

  Within this namespace live a few functions and macros, all tailored
  towards one single purpose: extracting various primitive types out
  of a ByteBuffer. The goal is to be able to write a simple C
  struct-like specification for this purpose."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "Eclipse Public License - v 1.0"
              :url "http://www.eclipse.org/legal/epl-v10.html"}}

  (:use [balabit.blobbity.typecast])
  (:import (java.nio ByteBuffer)))

;; We need to pre-declare `decode-blob`, because certain frame
;; decoders (`:struct`, in particular) will need to use it.
(declare decode-blob)

(defmulti decode-frame
  "Decode a single frame from a `ByteBuffer` of the specified type.
  Depending on the type, one or more options may be specified.

  Examples:

    (decode-frame buffer :byte) ;=> 42
    (decode-frame buffer :string 5) ;=> \"MAGIC\""

  {:arglists '([buffer type & options])}

  (fn [#^ByteBuffer _ type & _] type))

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

;; Decoding a signed byte is done via the `get` method of
;; ByteBuffer. The result is coerced into a byte, and no
;; transformation is applied.
(defdfm :byte .get byte identity)
;; Decoding an unsigned byte is similar, with the difference being that
;; a byte->ubyte transformation gets applied too.
(defdfm :ubyte .get byte byte->ubyte)

;; Decoding a signed 16-bit integer is doen via the `getShort' method
;; of ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int16 .getShort short identity)
;; Decoding an unsigned 16-bit integer is similar, with the difference
;; being that a short->ushort transformation gets applied too.
(defdfm :uint16 .getShort short short->ushort)

;; Decoding a signed 32-bit integer is doen via the `getInt' method of
;; ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int32 .getInt int identity)
;; Decoding an unsigned 32-bit integer is similar, with the difference
;; being that a int->uint transformation gets applied too.
(defdfm :uint32 .getInt int int->uint)

;; Decoding a signed 64-bit integer is doen via the `getLong' method
;; of ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defdfm :int64 .getLong long identity)
;; Decoding an unsigned 64-bit integer is similar, with the difference
;; being that a long->ulong transformation gets applied too.
(defdfm :uint64 .getLong long long->ulong)

;; Decoding a string of a specific length is done by reading the
;; required amount of bytes into an array, and turning that back into
;; a string.
(defmethod decode-frame :string
  [#^ByteBuffer buffer _ length]

  (let [b (byte-array length)
        _ (.get buffer b)]
    (String. b)))

;; Decoding NULL-terminated C-like strings is slightly more
;; complicated, as the buffer must be read until the first NULL-byte
;; only, but we have no indication of its length.
;;
;; The result shall be the string itself, without the trailing
;; NULL-byte.
(defmethod decode-frame :c-string
  [#^ByteBuffer buffer _]

  (loop [acc []]
    (let [c (decode-frame buffer :byte)]
      (if (zero? c)
        (String. (byte-array acc))
        (recur (conj acc c))))))

;; A construct that can be observed often, is a length-prefixed
;; string. To make it easy to decode these, `:prefixed-string` can be
;; used, which takes a single parameter, the type of the prefix.
(defmethod decode-frame :prefixed-string
  [#^ByteBuffer buffer _ prefix-type]

  (let [len (decode-frame buffer prefix-type)]
    (decode-frame buffer :string len)))

;; Mostly for aesthetic reasons, it is sometimes advisable to have
;; deeper nesting in the returned map. This is best achieved by
;; introducing a special `:struct` frame decoder, which dispatches to
;; `decode-blob`, defined just below.
(defmethod decode-frame :struct
  [#^ByteBuffer buffer _ struct-spec]

  (decode-blob buffer struct-spec))

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
  [buffer _ length]

  (let [blob (-> buffer .slice (.limit length) (.order (.order buffer)))]
    (decode-frame buffer :skip length)
    blob))

;; ----------------------------------------------------------------

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

;; If the elem-spec is not a keyword, then assume it is a [type
;; options] vector, so destructure it, and pass it onwards.
(defmethod decoder-dispatch :default
  [#^ByteBuffer buffer [type options]]

  (decode-frame buffer type options))

(defn- spec-dispatch
  "Used by `decode-blob`, this function receives a buffer, a map and a
  key-spec pair, and depending on a few things, decides how to proceed
  with them.

  If the key is `:skip`, then it will skip as many bytes as specified,
  otherwise it will dispatch to `decoder-dispatch` to get the value,
  and assoc it into the result if it is not `nil`. If it is, the map
  will be returned unchanged."

  [buffer m [key elem-spec]]

  (if (= key :skip)
    (decode-frame buffer :skip elem-spec)
    (let [v (decoder-dispatch buffer elem-spec)]
      (if v
        (assoc m key v)
        m))))

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
  (reduce (partial spec-dispatch buffer) {} (partition 2 spec)))

(defn decode-blob-array
  "Decode all frames of the same type from a ByteBuffer. Use this when
  you have a buffer that contains an unspecified number of frames of
  the same type.

  Returns a lazy sequence."

  [#^ByteBuffer buffer type]

  (let [step (fn [s buffer type]
               (when-not (= (.position buffer) (.limit buffer))
                 (cons (decode-frame buffer type)
                       (decode-blob-array buffer type))))]
    (lazy-seq (step [] buffer type))))
