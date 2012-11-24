(ns balabit.blobbity.typecast
  "Typecasting helper functions."

  (:import (java.nio ByteBuffer)))

(defn byte->ubyte
  "Convert a signed byte to unsigned."
  [x]
  (bit-and 0xFF (Short. (short x))))

(defn short->ushort
  "Convert a signed short to unsigned."
  [x]
  (bit-and 0xFFFF (Integer. (int x))))

(defn int->uint
  "Convert a signed integer to unsigned."
  [x]
  (bit-and 0xFFFFFFFF (Long. (long x))))

(defn- long->byte-array
  "Convert a long to a byte array"
  [^long n]
  (-> (ByteBuffer/allocate 8) (.putLong n) .array))

(defn long->ulong
  "Convert a signed long to unsinged."
  [x]
  (let [^bytes magnitude (long->byte-array x)]
    (bigint (BigInteger. 1 magnitude))))
