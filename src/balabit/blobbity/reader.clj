(ns balabit.blobbity.reader
  "Binary blob reader functions.

  Within this namespace live two functions and a macro, all tailored
  towards one single purpose: extracting various primitive types out
  of a ByteBuffer. The goal is to be able to write a simple C
  struct-like specification for this purpose."

  ^{:author "Gergely Nagy <algernon@balabit.hu>"
    :copyright "Copyright (C) 2012 Gergely Nagy <algernon@balabit.hu>"
    :license {:name "GNU General Public License - v3"
              :url "http://www.gnu.org/licenses/gpl.txt"}}

  (:use balabit.blobbity.typecast)
  (:import (java.nio ByteBuffer)))

;; We need to pre-declare read-spec, because certain readers
;; (`:struct`, in particular) will need to use it.
(declare read-spec)

(defmulti read-element
  "Read a single element from a `ByteBuffer` of the specified type.
  Depending on the type, one or more options may be specified.

  Examples:

    (read-element buffer :byte) ;=> 42
    (read-element buffer :string 5) ;=> \"MAGIC\""

  {:arglists '([buffer type & options])}

  (fn [#^ByteBuffer buffer type & params] type))

(defmacro defelement-reader
  "Create a method for the `read-element` multi-method (using
  `dispatch`), one that uses `getter` to extract the value, `typecast`
  to coerce the results into a given type, and finally, apply the
  `transform` function before returning."

  [dispatch getter typecast transform]

  `(defmethod read-element ~dispatch [#^ByteBuffer buffer# _#]
     (-> (~getter buffer#)
         (~typecast)
         (~transform))))

;; Reading a signed byte is done via the `get` method of
;; ByteBuffer. The result is coerced into a byte, and no transformation
;; is applied.
(defelement-reader :byte .get byte identity)
;; Reading an unsigned byte is similar, with the difference being that
;; a byte->ubyte transformation gets applied too.
(defelement-reader :ubyte .get byte byte->ubyte)

;; Reading a signed 16-bit integer is doen via the `getShort' method
;; of ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defelement-reader :int16 .getShort short identity)
;; Reading an unsigned 16-bit integer is similar, with the difference
;; being that a short->ushort transformation gets applied too.
(defelement-reader :uint16 .getShort short short->ushort)

;; Reading a signed 32-bit integer is doen via the `getInt' method
;; of ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defelement-reader :int32 .getInt int identity)
;; Reading an unsigned 32-bit integer is similar, with the difference
;; being that a int->uint transformation gets applied too.
(defelement-reader :uint32 .getInt int int->uint)

;; Reading a signed 64-bit integer is doen via the `getLong' method
;; of ByteBuffer. The result is coerced into a short, and no
;; transformation is applied.
(defelement-reader :int64 .getLong long identity)
;; Reading an unsigned 64-bit integer is similar, with the difference
;; being that a long->ulong transformation gets applied too.
(defelement-reader :uint64 .getLong long long->ulong)

;; Reading a string of a specific length is done by reading the
;; required amount of bytes into an array, and turning that back into
;; a string.
(defmethod read-element :string
  [#^ByteBuffer buffer _ length]

  (let [b (byte-array length)
        _ (.get buffer b)]
    (String. b)))

;; Reading NULL-terminated C-like strings is slightly more
;; complicated, as the buffer must be read until the first NULL-byte
;; only, but we have no indication of its length.
;;
;; The result shall be the string itself, without the trailing
;; NULL-byte.
(defmethod read-element :c-string
  [#^ByteBuffer buffer _]

  (loop [acc []]
    (let [c (read-element buffer :byte)]
      (if (= c 0)
        (String. (byte-array acc))
        (recur (conj acc c))))))

;; A construct that can be observed often, is a length-prefixed
;; string. To make it easy to read these, `:prefixed-string` can be
;; used, which takes a single parameter, the type of the prefix.
(defmethod read-element :prefixed-string
  [#^ByteBuffer buffer _ prefix-type]

  (let [len (read-element buffer prefix-type)]
    (read-element buffer :string len)))

;; Mostly for aesthetic reasons, it is sometimes advisable to have
;; deeper nesting in the returned map. This is best achieved by
;; introducing a special `:struct` reader, which dispatches to
;; `read-spec`, defined just below.
(defmethod read-element :struct
  [#^ByteBuffer buffer _ struct-spec]

  (read-spec buffer struct-spec))

;; ----------------------------------------------------------------

(defmulti read-spec-dispatch
  "Given an element spec, dispatch it to the appropriate
  `read-element` call."

  {:arglists '([buffer type] [buffer [type params]])}

  (fn [#^ByteBuffer buffer options]
    (class options)))

;; If the elem-spec is a keyword, pass it on to read-element as-is.
(defmethod read-spec-dispatch clojure.lang.Keyword
  [#^ByteBuffer buffer type]
  (read-element buffer type))

;; If the elem-spec is not a keyword, then assume it is a [type
;; options] vector, so destructure it, and pass it onwards.
(defmethod read-spec-dispatch :default
  [#^ByteBuffer buffer [type options]]

  (read-element buffer type options))

(defn read-spec
  "Read multiple elements from a ByteBuffer, according to a
  specification. The specification is a vector of key-value pairs,
  where the values will be used to dispatch `read-element` on. If the
  particular type needs extra arguments, then the type itself and the
  extra parameters must be put in a vector.

  Examples:

    (read-spec buffer [:magic [:string 4]
                       :header-length :uint32])"

  [#^ByteBuffer buffer spec]

  (assert (even? (count spec)))
  (let [pairs (partition 2 spec)
        assoc-fn (fn [m [key elem-spec]]
                   (assoc m key (read-spec-dispatch buffer elem-spec)))]
    (reduce assoc-fn {} pairs)))
