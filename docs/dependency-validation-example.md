# Dependency Inference Example

This example demonstrates how Frontseat infers Backstage relationships from build tool dependencies.

## Valid Project Example

### catalog-info.yaml
```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: user-service
spec:
  type: service
  lifecycle: production
  owner: backend-team
  # Dependencies are inferred from pom.xml automatically
  # Only specify here if they can't be inferred
```

### pom.xml (Maven)
```xml
<dependencies>
  <!-- ✅ AUTO-INFERRED: These become Backstage relationships -->
  <dependency>
    <groupId>com.frontseat</groupId>
    <artifactId>auth-service</artifactId>  <!-- → dependsOn: auth-service -->
  </dependency>
  <dependency>
    <groupId>com.frontseat</groupId>
    <artifactId>data-lib</artifactId>       <!-- → dependsOn: data-lib -->
  </dependency>
  <dependency>
    <groupId>com.frontseat</groupId>
    <artifactId>notification-api</artifactId> <!-- → consumesApis: notification-api -->
  </dependency>
  
  <!-- ✅ IGNORED: External dependencies not converted to relationships -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
</dependencies>
```

**Result**: ✅ Dependencies automatically inferred and added to project graph

## Example With Explicit Dependencies

When relationships can't be inferred (e.g., runtime-only or conceptual dependencies), declare them explicitly:

### catalog-info.yaml
```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: order-service
spec:
  type: service
  lifecycle: production
  owner: backend-team
  dependsOn:
    # ✅ This can't be inferred from pom.xml (runtime dependency)
    - component:message-queue
  consumesApis:
    # ✅ This can't be inferred (REST API call, not Maven dependency)
    - external-payment-api
```

### pom.xml (Maven)
```xml
<dependencies>
  <!-- ✅ AUTO-INFERRED: Will be added to dependsOn -->
  <dependency>
    <groupId>com.frontseat</groupId>
    <artifactId>auth-service</artifactId>
  </dependency>
  <dependency>
    <groupId>com.frontseat</groupId>
    <artifactId>data-lib</artifactId>
  </dependency>
  
  <!-- No Maven dependency for message-queue (runtime-only) -->
  <!-- No Maven dependency for external-payment-api (REST calls) -->
</dependencies>
```

**Result**: ✅ Final dependency graph includes both inferred (auth-service, data-lib) and explicit (message-queue, external-payment-api) dependencies

## Supported Tools

### Maven (pom.xml)
- Parses `<dependency>` elements
- Maps `groupId:artifactId` to project names
- Only validates internal dependencies (configurable by `groupId` patterns)

### NPM/Yarn/PNPM (package.json)
```json
{
  "dependencies": {
    "@yourcompany/user-api": "^1.0.0",     // ✅ Internal - validated
    "react": "^18.2.0"                     // ✅ External - ignored
  }
}
```


## Configuration

Internal dependency patterns are configurable in `DependencyValidationService`:

```kotlin
// Maven: Only validate com.frontseat.* dependencies
groupId.startsWith("com.frontseat")

// NPM: Only validate @yourcompany/* dependencies  
packageName.startsWith("@yourcompany/")

```

## Benefits

1. **Automatic Accuracy**: Backstage catalog automatically reflects actual code dependencies
2. **Reduced Manual Work**: No need to manually maintain dependency lists in catalog-info.yaml
3. **Source of Truth**: Build tools (pom.xml, package.json) are the authoritative source for dependencies
4. **Change Impact**: Accurate dependency graphs enable better impact analysis
5. **Hybrid Approach**: Supports both automatic inference and explicit declarations when needed

## When to Use Explicit Dependencies

Use explicit dependencies in catalog-info.yaml only when they can't be inferred:

1. **Runtime Dependencies**: Services that are called at runtime but not compile-time dependencies
2. **External APIs**: REST/GraphQL APIs that aren't package dependencies
3. **Infrastructure Dependencies**: Databases, message queues, etc.
4. **Conceptual Dependencies**: Dependencies that exist at the business logic level

The system follows: **Tool Dependencies + Explicit Dependencies = Complete Dependency Graph**