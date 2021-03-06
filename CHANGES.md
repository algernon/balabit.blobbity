# balabit.blobbity 0.1.2 (2013-03-07)

## New decoders

### `(decode-frame *buffer* :sequence *type* & *params*)`

The new `:sequence` decoder supersedes the former `decode-blob-array`
function (which is kept for backwards compatibility). Given a `:type`
and any extra parameters, it returns a lazy sequence of decoded frames
of the given type.

# balabit.blobbity 0.1.1 (2013-01-06)

## New decoders

### `(decode-frame *buffer* :array *size*)`

The new `:array` decoder works very similarly to the existing
`:slice`, but instead of returning a `ByteBuffer` slice, it returns a
`byte[]` array.

The main use case for these is when the result needs to be fed to a
Java function that expects an array, such as the String constructor or
the various message digest algorithms.

# balabit.blobbity 0.1.0 (2012-12-09)

Initial release.
