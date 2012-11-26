(ns balabit.blobbity.test
  (:use [clojure.test])
  (:require [balabit.blobbity :as blob])
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

(defn make-test-buffer []
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

(deftest decode-frame-test
  (testing "Single frame decoding"
    (testing "of numeric types"
      (is (= (blob/decode-frame (minus-one-buffer 1) :byte) -1))
      (is (= (blob/decode-frame (minus-one-buffer 1) :ubyte) 255))
      (is (= (blob/decode-frame (minus-one-buffer 2) :int16) -1))
      (is (= (blob/decode-frame (minus-one-buffer 2) :uint16) 65535))
      (is (= (blob/decode-frame (minus-one-buffer 4) :int32) -1))
      (is (= (blob/decode-frame (minus-one-buffer 4) :uint32) 4294967295))
      (is (= (blob/decode-frame (minus-one-buffer 8) :int64) -1))
      (is (= (blob/decode-frame (minus-one-buffer 8) :uint64) 18446744073709551615N)))

    (testing "of string-y types"

      (is (= (blob/decode-frame (wrap-string-in-buffer "MAGIC") :string 5) "MAGIC"))
      (is (= (blob/decode-frame (wrap-string-in-buffer "MAGIC\0") :c-string) "MAGIC"))

      (is (= (blob/decode-frame (wrap-string-in-prefixed-buffer "Awesome!")
                                :prefixed-string :int32) "Awesome!")))

    (testing "of composite types"

      (is (= (blob/decode-frame (wrap-string-in-buffer "MAGIC")
                                :struct [:magic? [:string 5]])
             {:magic? "MAGIC"}))

      (is (= (.limit (blob/decode-frame (minus-one-buffer 4) :slice 2)) 2)))))

(deftest decode-blob-test
  (testing "Blob decoding"
    (testing "of a flat blob spec"
      (let [flat-spec [:magic-int32 :int32
                       :magic-int16 :int16
                       :magic-byte :byte
                       :magic-ubyte :ubyte
                       :magic-int64 :int64
                       :magic-uint64 :uint64
                       :magic-prefixed-string [:prefixed-string :byte]
                       :magic-string [:string 5]
                       :magic-c-string :c-string]]

        (is (= (blob/decode-blob (make-test-buffer) flat-spec)
               {:magic-int32 16843009
                :magic-int16 514
                :magic-byte -1
                :magic-ubyte 255
                :magic-int64 -1
                :magic-uint64 18446744073709551615N
                :magic-string "MAGIC"
                :magic-prefixed-string "h"
                :magic-c-string "c-string"}))))


    (testing "of a nested blob spec"
      (let [nested-spec [:ints [:struct [:int32 :int32
                                         :int16 :int16
                                         :byte :byte
                                         :ubyte :ubyte
                                         :int64 :int64
                                         :uint64 :uint64]]
                         :strings [:struct [:prefixed [:prefixed-string :byte]
                                            :string [:string 5]
                                            :c-string :c-string]]]]
        (is (= (blob/decode-blob (make-test-buffer) nested-spec)
               {:ints {:int32 16843009
                       :int16 514
                       :byte -1
                       :ubyte 255
                       :int64 -1
                       :uint64 18446744073709551615N}
                :strings {:prefixed "h"
                          :string "MAGIC"
                          :c-string "c-string"}}))))

    (testing "of skipping bytes from within a spec"
      (let [skip-spec [:dummy [:skip 4]
                       :skip 2
                       :byte :byte]]
        (is (= (blob/decode-blob (make-test-buffer) skip-spec)
               {:byte -1}))))

    (testing "with slicing the buffer up in the process"
      (let [test-buffer (make-test-buffer)
            outer-spec [:two-ints [:slice 6]
                        :byte :byte]
            inner-spec [:int32 :int32
                        :int16 :int16]
            ubyte-spec [:ubyte :ubyte]

            inner-stuff (blob/decode-blob test-buffer outer-spec)]
        (is (= (:byte inner-stuff) -1))
        (is (= (blob/decode-blob test-buffer ubyte-spec) {:ubyte 255}))
        (is (= (blob/decode-blob (:two-ints inner-stuff) inner-spec)
               {:int32 16843009
                :int16 514}))))))

(deftest decode-blob-array-test
  (testing "Decoding multiple homogenous frames from a ByteBuffer"
    (is (= (blob/decode-blob-array (minus-one-buffer 10) :byte)
           [-1 -1 -1 -1 -1 -1 -1 -1 -1 -1]))

    (testing "... lazily"
      (let [buff (minus-one-buffer 10)]
        (is (= (take 2 (blob/decode-blob-array buff :byte))
               [-1 -1]))
        (is (= (.position buff) 2))
        (is (= (blob/decode-blob-array buff :byte)
               [-1 -1 -1 -1 -1 -1 -1 -1]))))))
