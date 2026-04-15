# RSConfig Usage

This module adds RuneScape-focused preprocessing before TOML decoding.

## Entry Points

Use the mapper extensions in `dev.openrune.toml.rsconfig`:

- `decodeRuneScape<T>(...)`
- `decodeRuneScapeList<T>(...)`
- `decodeRuneScapeValue<T>(...)`

## Mapper Configuration

```kotlin
val mapper = tomlMapper {
    rsconfig {
        allowedTableHeaders("config", "config2")
        enableConstantProvider()
        enabledTokenizedReplacement(
            mapOf("param_key_one" to "hey3")
        )
    }
}
```

## Tokenized Replacement

### Local tokens (inside TOML file)

```toml
[[tokenizedReplacement]]
first_settings = 90
param_key_one = "dffd"

[[config]]
settings = "%first_settings%"
[config.params]
"%param_key_one%" = 34
```

### Global tokens (from mapper DSL)

Global keys must use `%global.<name>%`.

```toml
[[config]]
settings = 90
[config.params]
"obj.shark" = "%global.param_key_one%"
```

```kotlin
enabledTokenizedReplacement(mapOf("param_key_one" to "hey3"))
```

### Rules

- `%token%` resolves from local `[[tokenizedReplacement]]`
- `%global.token%` resolves from mapper global replacements
- local blocks cannot define `global.*` keys
- token placeholders must be quoted (`"%token%"`)

## Constant Replacement

Constant replacement resolves quoted keys like `"obj.shark"` using `ConstantProvider`.

```kotlin
ConstantProvider.load(File("..."))
```

```toml
[[config]]
settings = 90
[config.params]
"obj.shark" = 34
```

With `enableConstantProvider()`, `"obj.shark"` is replaced by its numeric id before decode.

## Combined Example

```toml
[[tokenizedReplacement]]
param_key_two = "obj.shark"

[[config]]
settings = 345
[config.params]
"%param_key_two%" = "%global.param_key_one%"
```

```kotlin
enabledTokenizedReplacement(mapOf("param_key_one" to "hey3"))
enableConstantProvider()
```

Result after preprocessing:

```toml
[config.params]
"385" = "hey3"
```
