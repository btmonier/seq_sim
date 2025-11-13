# seq_sim

## Requirements

- Java 21
- Gradle (included via wrapper)
- [pixi](https://pixi.sh/) for managing the virtual environment

## Setup

### 1. Build the application

```bash
./gradlew build
```

### 2. Run environment setup

```bash
./gradlew run --args="setup-environment"
```

Or with a custom working directory:

```bash
./gradlew run --args="setup-environment --work-dir /path/to/workdir"
```

This command will:
- Copy the `pixi.toml` configuration to the current directory
- Install the pixi environment with all dependencies (Python 3.10, NumPy, Pandas, AnchorWave on Linux, minimap2, agc, ropebwt3)
- Download and extract the MLImpute repository to `<work-dir>/src/MLImpute/`

Default working directory: `seq_sim_work`

### 3. Activate the pixi environment

```bash
pixi shell
```

## Available Commands

### setup-environment

Initialize the environment and download dependencies.

**Options:**
- `--work-dir`, `-w`: Working directory for files and scripts (default: `seq_sim_work`)

**Example:**
```bash
./gradlew run --args="setup-environment -w /path/to/my/workdir"
```

## Development

This project uses:
- **Kotlin 2.2.20** with JVM target (Java 21)
- **Clikt 5.0.3** for command-line interface
- **Log4j2 2.24.3** for logging
- **Gradle** with Kotlin DSL for build management
- **pixi** for environment management with bioconda tools

### Project Structure

```
src/main/kotlin/
├── Main.kt                           # Application entry point
├── net/maizegenetics/
│   ├── commands/
│   │   └── SetupEnvironment.kt      # setup-environment command
│   └── utils/
│       ├── FileDownloader.kt        # File download and extraction utilities
│       └── ProcessRunner.kt         # Process execution utilities
└── resources/
    ├── pixi.toml                    # Pixi environment configuration
    └── log4j2.xml                   # Logging configuration
```

### Common Commands

**Run tests:**
```bash
./gradlew test
```

**Clean build:**
```bash
./gradlew clean build
```

**Run a single test:**
```bash
./gradlew test --tests "ClassName.testMethodName"
```

**View logs:**
Logs are written to `logs/seq_sim.log` and displayed in the console.
