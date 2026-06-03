!!! tip "For multi-module projects"

Furthermore, a `String` binding cannot satisfy a `String?` automatically. You _may_ however `@Binds` a `String` to a `String?` and Metro will treat it as a valid binding.


??? note "Implementation Notes"
    Telling a story

```diff
  def greet(name):
-     print("Hello " + name)
+     print(f"Hello {name}, welcome to Zensical!")
```

??? note "Implementation Notes"
Telling a story

```diff
 def greet(name):
-    print("Hello " + name)
+    print(f"Hello {name}, welcome to Zensical!")
```