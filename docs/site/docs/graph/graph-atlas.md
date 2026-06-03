```kotlin
@DependencyGraph
interface AppGraph {
  val cacheProvider: () -> Cache
}
```