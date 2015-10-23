# onyx-test

This reproduces a bug in an onyx use case wherein a job submits another job.

## Usage

    lein run

If you do not already have an Aeron driver running then you need to turn the embedded driver on before running (change to this):

    :onyx.messaging.aeron/embedded-driver? true

## Expected Output

If everything was working we should see something like this:

    SUBMITTING OUTER
    INPUT OUTER SEGMENTS
    OUTER! {:outer suffix}
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}
    CCCCCCCCCCCCC
    BBBBBBBBBBBBB
    AAAAAAAAAAAAA
    BBBBBBBBBBBBB
    BBBBBBBBBBBBB
    CCCCCCCCCCCCC
    BBBBBBBBBBBBB
    AAAAAAAAAAAAA
    CCCCCCCCCCCCC
    CCCCCCCCCCCCC
    AAAAAAAAAAAAA
    AAAAAAAAAAAAA
    INNER SEGMENTS COMPLETE!
    OUTER SEGMENTS COMPLETE!
    [{:outer "prefix-suffix"
      :inner
      [{:inner "b-what"}
       {:inner "b-context"}
       {:inner "b-is"}
       {:inner "b-this?"}
       {:inner "a-what"}
       {:inner "a-context"}
       {:inner "a-is"}
       {:inner "a-this?"}
       {:inner "c-what"}
       {:inner "c-context"}
       {:inner "c-is"}
       {:inner "c-this?"}
       :done]}
     :done]

## Actual Output

This is what was actually witnessed when running:

    SUBMITTING OUTER
    INPUT OUTER SEGMENTS
    OUTER! {:outer suffix}
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}
    OUTER! {:outer suffix}
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}
    OUTER! OUTER! {{:inner:inner  whatwhat}}
    
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}
    OUTER! {:outer suffix}
    SUBMITTING INNER
    INPUT INNER SEGMENT {:inner what}
    INPUT INNER SEGMENT {:inner context}
    INPUT INNER SEGMENT {:inner is}
    INPUT INNER SEGMENT {:inner this?}

Somehow the outer job was submitted multiple times, and the inner job never ran.

## License

Copyright © 2015 Little Bird

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
