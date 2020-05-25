(ns location.macros
  "Provides useful Clojure macros. These should be abstract to be common to many applications.")

(defmacro nil->
  "Threads expr through forms, using ->, until a non nil value is returned. If a form returns nil, expr is threaded to the next."
  [expr & forms]
  (let [g (gensym)
        pstep (fn [step] `(if (nil? ~g) (-> ~expr ~step) ~g))]
    `(let [~g nil
           ~@(interleave (repeat g) (map pstep forms))]
      ~g)))

(defmacro nil->>
  "Threads expr through forms, using ->>, until a non nil value is returned. If a form returns nil,expr is threaded to the next."
  [expr & forms]
  (let [g (gensym)
        pstep (fn [step] `(if (nil?  ~g) (->> ~expr ~step) ~g))]
    `(let [~g nil
           ~@(interleave (repeat g) (map pstep forms))]
      ~g)))      

(defmacro conj->
  "Threads expr through all forms, using ->, conjing the results into coll."
  [expr coll & forms]
  (let [g (gensym)
        pstep (fn [step] `(conj ~g (-> ~expr ~step)))]
    `(let [~g ~coll
           ~@(interleave (repeat g) (map pstep forms))]
       (flatten ~g))))

(defmacro conj->>
  "Threads expr through all forms, using ->>, conjing the results into coll."
  [expr coll & forms]
  (let [g (gensym)
        pstep (fn [step] `(conj ~g (->> ~expr ~step)))]
    `(let [~g ~coll
           ~@(interleave (repeat g) (map pstep forms))]
       (flatten ~g))))
