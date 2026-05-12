package aspector

enum class Using {
  OVERRIDE,
  REPLACE,

  //MIXIN
  BEFORE,
  BEFORE_RETURN,
  AFTER,
  AFTER_RETURN,
}