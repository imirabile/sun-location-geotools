(ns location.utils.placeid
  (:require [clojure.tools.logging :as log]
            [clojure.string        :as str]
            [location.config       :as cfg]
            [crypto.random         :as r])
  (:import  [javax.crypto Cipher SecretKeyFactory]
            [javax.crypto.spec SecretKeySpec PBEKeySpec IvParameterSpec]
            [java.net URLEncoder URLDecoder]
            [org.apache.commons.codec.binary Base64 Hex])
  (:gen-class))

(def ^:private cipher-algorithm "AES/CBC/PKCS5Padding")
(def ^:private offset 0)
(def ^:private length 16)
(def ^:private key-algorithm "AES")

(defn ^:private get-secret-key
  "Fetches the secret key from configuration and produce a SecretKeySpec."
  [algorithm]
  (let [secret (Base64/decodeBase64 (cfg/get-config-first "aesSecretKey"))]
    (SecretKeySpec. secret algorithm)))

(defn ^:private get-iv
  "Fetches the configured IV and produces an IvParameterSpec."
  [offset length]
  (let [iv (Base64/decodeBase64 (cfg/get-config-first "aesIV"))]
    (IvParameterSpec. iv offset length)))

(defn ^:private encrypt-message
  "Encrypts the given payload"
  [place]
  (try
    (let [place (format "%1$-31s" place)
          secret (get-secret-key key-algorithm)
          cipher (Cipher/getInstance cipher-algorithm)
          params (do
                   (.init cipher Cipher/ENCRYPT_MODE secret (get-iv offset length))
                   (.getParameters cipher))
          cipher-text (.doFinal cipher (.getBytes place "UTF-8"))] 
      (Hex/encodeHexString cipher-text))
    (catch Exception ex
      (log/debug "Exception encrypting " place)
      (log/error ex))))

(defn ^:private decrypt-message
  "Decrypts the given message"
  [message]
  (try
    (let [cipher (Cipher/getInstance cipher-algorithm)
          secret (get-secret-key key-algorithm)]
      (.init cipher Cipher/DECRYPT_MODE secret (get-iv offset length))
      (str/trim (String. (.doFinal cipher (Hex/decodeHex (char-array message))) "UTF-8")))
    (catch Exception ex
      (log/debug "Exception decrypting " message)
      (log/error ex))))

(defn get-place
  "Decrypts the given place id, returning the place."
  [id]
  (decrypt-message id))

(defn get-id
  "Encrypts the given id, returning a place id."
  [place & args]
  (encrypt-message (apply str place args)))
