(ns balabit.blobbity.test.reader
  (:use [clojure.test])
  (:require [balabit.blobbity.reader :as blob])
  (:import java.nio.ByteBuffer))

(defn minus-one-buffer [n]
  (ByteBuffer/wrap (byte-array (take n (repeat (byte -1))))))

(defn wrap-string-in-buffer [string]
  (ByteBuffer/wrap (byte-array (map #(byte (int %1)) string))))

(defn wrap-string-in-prefixed-buffer [string]
  (let [buffer (byte-array (+ 4 (count string)))
        b (ByteBuffer/wrap buffer)]
    (.putInt b (count string))
    (dorun (map #(.put b (byte (int %1))) string))
    (.position b 0)))

(defn spec-buffer []
  (ByteBuffer/wrap
   (byte-array
    (apply conj [(byte 1)
                 (byte 1)
                 (byte 1)
                 (byte 1) ; 16843009

                 (byte 2)
                 (byte 2) ; 514

                 (byte -1)
                 (byte -1)

                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)

                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)
                 (byte -1)

                 ; prefixed-string
                 (byte 1)
                 (byte (int \h))
                 ]

           (apply conj
                  (vec (doall (map #(byte (int %1)) "MAGIC")))
                  (vec (doall (map #(byte (int %1)) "c-string\0"))))))))

(def test-spec
  [:magic-int32 :int32
   :magic-int16 :int16
   :magic-byte :byte
   :magic-ubyte :ubyte
   :magic-int64 :int64
   :magic-uint64 :uint64
   :magic-prefixed-string [:prefixed-string :byte]
   :magic-string [:string 5]
   :magic-c-string :c-string])

(deftest read-element
  (testing "blob/read-element"
    (is (= (blob/read-element (minus-one-buffer 1) :byte) -1))
    (is (= (blob/read-element (minus-one-buffer 1) :ubyte) 255))
    (is (= (blob/read-element (minus-one-buffer 2) :int16) -1))
    (is (= (blob/read-element (minus-one-buffer 2) :uint16) 65535))
    (is (= (blob/read-element (minus-one-buffer 4) :int32) -1))
    (is (= (blob/read-element (minus-one-buffer 4) :uint32) 4294967295))
    (is (= (blob/read-element (minus-one-buffer 8) :int64) -1))
    (is (= (blob/read-element (minus-one-buffer 8) :uint64) 18446744073709551615N))

    (is (= (blob/read-element (wrap-string-in-buffer "MAGIC") :string 5) "MAGIC"))
    (is (= (blob/read-element (wrap-string-in-buffer "MAGIC\0") :c-string) "MAGIC"))

    (is (= (blob/read-element (wrap-string-in-prefixed-buffer "Awesome!")
                              :prefixed-string :int32) "Awesome!"))))

(deftest read-spec
  (testing "blob/read-spec"
    (is (= (blob/read-spec (spec-buffer) test-spec)
           {:magic-int32 16843009
            :magic-int16 514
            :magic-byte -1
            :magic-ubyte 255
            :magic-int64 -1
            :magic-uint64 18446744073709551615N
            :magic-string "MAGIC"
            :magic-prefixed-string "h"
            :magic-c-string "c-string"}))))
