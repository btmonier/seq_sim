# seq_sim

Sequuence simulator pipeline and orchestrator for MLImpute

## Requirements

- Java 21
- Gradle (included via wrapper)
- [pixi](https://pixi.sh/) for managing the virtual environment

## Quick Start

### 1. Build the application

```bash
./gradlew build
```

### 2. Run environment setup

```bash
./gradlew run --args="setup-environment"
```

This initializes the pixi environment and downloads required tools. Default working directory: `seq_sim_work`

### 3. Run the pipeline

```bash
# Align assemblies
./gradlew run --args="align-assemblies --ref-gff reference.gff --ref-fasta reference.fa --query-fasta queries/"

# Convert to GVCF
./gradlew run --args="maf-to-gvcf --reference-file reference.fa --maf-file seq_sim_work/output/01_anchorwave_results/maf_file_paths.txt"
```

## Pipeline Overview

The pipeline consists of three sequential commands:

1. **setup-environment** (00): Initialize the pixi environment and download required tools
2. **align-assemblies** (01): Align query assemblies to a reference using AnchorWave and minimap2
3. **maf-to-gvcf** (02): Convert MAF alignments to compressed GVCF format

Each command generates logs in `<work-dir>/logs/` and outputs in `<work-dir>/output/`.

## Commands

### 1. setup-environment

Initializes the environment and downloads dependencies.

**Usage:**
```bash
./gradlew run --args="setup-environment [OPTIONS]"
```

**Options:**
- `--work-dir`, `-w`: Working directory for files and scripts (default: `seq_sim_work`)

**What it does:**
- Copies `pixi.toml` to the working directory
- Installs the pixi environment with all dependencies:
  - Python 3.10, NumPy, Pandas
  - Java 21 (OpenJDK)
  - minimap2 2.28
  - AnchorWave (Linux only)
  - agc 3.1, ropebwt3 3.8
- Downloads and extracts MLImpute repository to `<work-dir>/src/MLImpute/`
- Downloads and extracts biokotlin-tools to `<work-dir>/src/biokotlin-tools/`

**Output:**
- `<work-dir>/pixi.toml`
- `<work-dir>/.pixi/` (pixi environment)
- `<work-dir>/src/MLImpute/`
- `<work-dir>/src/biokotlin-tools/`
- `<work-dir>/logs/00_setup_environment.log`

**Example:**
```bash
./gradlew run --args="setup-environment -w /path/to/workdir"
```

---

### 2. align-assemblies

Aligns multiple query assemblies to a reference genome using AnchorWave and minimap2.

**Usage:**
```bash
./gradlew run --args="align-assemblies [OPTIONS]"
```

**Options:**
- `--work-dir`, `-w`: Working directory (default: `seq_sim_work`)
- `--ref-gff`, `-g`: Reference GFF file (required)
- `--ref-fasta`, `-r`: Reference FASTA file (required)
- `--query-fasta`, `-q`: Query input - can be:
  - A single FASTA file (`.fa`, `.fasta`, `.fna`)
  - A directory containing FASTA files
  - A text file (`.txt`) with one FASTA file path per line
- `--threads`, `-t`: Number of threads to use (default: 1)

**What it does:**
1. Extracts CDS sequences from reference GFF using `anchorwave gff2seq`
2. Aligns reference to CDS with `minimap2` (once for all queries)
3. For each query:
   - Aligns query to CDS with `minimap2`
   - Runs `anchorwave proali` to generate alignments
   - Creates query-specific subdirectory with outputs
4. Generates `maf_file_paths.txt` listing all produced MAF files

**Output:**
- `<work-dir>/output/01_anchorwave_results/{refBase}_cds.fa` - Extracted CDS sequences
- `<work-dir>/output/01_anchorwave_results/{refBase}.sam` - Reference alignment
- `<work-dir>/output/01_anchorwave_results/{queryName}/` - Query-specific directory containing:
  - `{queryName}.sam` - Query alignment
  - `{refBase}_R1_{queryName}_Q1.anchors` - Anchor points
  - `{refBase}_R1_{queryName}_Q1.maf` - MAF alignment
  - `{refBase}_R1_{queryName}_Q1.f.maf` - Filtered MAF alignment
- `<work-dir>/output/01_anchorwave_results/maf_file_paths.txt` - List of MAF file paths
- `<work-dir>/logs/01_align_assemblies.log`

**Examples:**

Single query file:
```bash
./gradlew run --args="align-assemblies --ref-gff ref.gff --ref-fasta ref.fa --query-fasta query1.fa --threads 4"
```

Directory of query files:
```bash
./gradlew run --args="align-assemblies -g ref.gff -r ref.fa -q queries/ -t 8"
```

Text file with query paths:
```bash
# queries.txt contains:
# /path/to/query1.fa
# /path/to/query2.fa
# /path/to/query3.fa

./gradlew run --args="align-assemblies -g ref.gff -r ref.fa -q queries.txt -t 4"
```

---

### 3. maf-to-gvcf

Converts MAF alignment files to compressed GVCF format using biokotlin-tools.

**Usage:**
```bash
./gradlew run --args="maf-to-gvcf [OPTIONS]"
```

**Options:**
- `--work-dir`, `-w`: Working directory (default: `seq_sim_work`)
- `--reference-file`, `-r`: Reference FASTA file (required)
- `--maf-file`, `-m`: MAF input - can be:
  - A single MAF file (`.maf`)
  - A directory containing MAF files
  - A text file (`.txt`) with one MAF file path per line
- `--output-file`, `-o`: Output GVCF file name (optional, auto-generated for multiple files)
- `--sample-name`, `-s`: Sample name for GVCF (optional, defaults to MAF base name)

**What it does:**
1. Collects MAF files based on input pattern
2. For each MAF file:
   - Runs `biokotlin-tools maf-to-gvcf-converter` through pixi (ensures Java 21)
   - Generates compressed GVCF file (`.g.vcf.gz`)
   - Uses sample name from parameter or MAF file base name
3. Generates `gvcf_file_paths.txt` listing all produced GVCF files

**Output:**
- `<work-dir>/output/02_gvcf_results/{sampleName}.g.vcf.gz` - Compressed GVCF files
- `<work-dir>/output/02_gvcf_results/gvcf_file_paths.txt` - List of GVCF file paths
- `<work-dir>/logs/02_maf_to_gvcf.log`

**Examples:**

Single MAF file:
```bash
./gradlew run --args="maf-to-gvcf --reference-file ref.fa --maf-file sample.maf --sample-name Sample1"
```

Directory of MAF files:
```bash
./gradlew run --args="maf-to-gvcf -r ref.fa -m seq_sim_work/output/01_anchorwave_results/"
```

Text file with MAF paths (recommended):
```bash
./gradlew run --args="maf-to-gvcf -r ref.fa -m seq_sim_work/output/01_anchorwave_results/maf_file_paths.txt"
```

Custom output file (single MAF only):
```bash
./gradlew run --args="maf-to-gvcf -r ref.fa -m sample.maf -o custom_name.g.vcf.gz -s MySample"
```

## Complete Workflow Example

```bash
# 1. Build the project
./gradlew build

# 2. Set up the environment
./gradlew run --args="setup-environment -w my_work"

# 3. Prepare your input files
# - Reference: reference.fa, reference.gff
# - Queries: query1.fa, query2.fa, query3.fa

# 4. Align assemblies
./gradlew run --args="align-assemblies -w my_work -g reference.gff -r reference.fa -q queries/ -t 8"

# 5. Convert to GVCF
./gradlew run --args="maf-to-gvcf -w my_work -r reference.fa -m my_work/output/01_anchorwave_results/maf_file_paths.txt"

# 6. Your results are in:
# - MAF files: my_work/output/01_anchorwave_results/{queryName}/*.maf
# - GVCF files: my_work/output/02_gvcf_results/*.g.vcf.gz
# - Logs: my_work/logs/
```

## Working Directory Structure

After running the complete pipeline:

```
seq_sim_work/                              # Working directory
├── pixi.toml                              # Pixi configuration
├── pixi.lock                              # Pixi lock file
├── .pixi/                                 # Pixi environment
├── src/                                   # Downloaded tools
│   ├── MLImpute/                          # MLImpute repository
│   └── biokotlin-tools/                   # biokotlin-tools binary
├── logs/                                  # Log files
│   ├── 00_setup_environment.log
│   ├── 01_align_assemblies.log
│   └── 02_maf_to_gvcf.log
└── output/                                # Pipeline outputs
    ├── 01_anchorwave_results/             # Alignment results
    │   ├── {refBase}_cds.fa
    │   ├── {refBase}.sam
    │   ├── {queryName}/                   # Per-query subdirectory
    │   │   ├── {queryName}.sam
    │   │   ├── *.anchors
    │   │   ├── *.maf
    │   │   └── *.f.maf
    │   └── maf_file_paths.txt             # List of MAF files
    └── 02_gvcf_results/                   # GVCF results
        ├── *.g.vcf.gz                     # Compressed GVCF files
        └── gvcf_file_paths.txt            # List of GVCF files
```

## Important Notes

1. **Java 21 Requirement**: The biokotlin-tools app requires Java 21. The maf-to-gvcf command automatically runs it through pixi to ensure the correct Java version is used.

2. **AnchorWave on macOS**: AnchorWave is not available on macOS via conda. For macOS users, the align-assemblies command will not work. Use a Linux system or Docker container for the alignment step.

3. **Multi-File Input**: Commands support three input patterns for batch processing:
   - **Single file**: Direct path to a file
   - **Directory**: All files with matching extensions in the directory
   - **Text list**: Plain text file with one file path per line (recommended for large batches)

4. **Path Files**: Commands generate text files (`maf_file_paths.txt`, `gvcf_file_paths.txt`) listing all output files. These are useful for:
   - Downstream processing
   - Input to subsequent pipeline steps
   - Tracking which files were successfully generated

5. **Error Handling**: Commands continue processing remaining files if one fails, tracking success/failure counts. Check log files for detailed error information.

6. **Compressed Output**: GVCF files are automatically compressed with gzip (`.g.vcf.gz` extension) to save disk space.

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
├── Main.kt                              # Application entry point
├── net/maizegenetics/
│   ├── Constants.kt                     # Shared constants
│   ├── commands/
│   │   ├── SetupEnvironment.kt          # 00: setup-environment command
│   │   ├── AlignAssemblies.kt           # 01: align-assemblies command
│   │   └── MafToGvcf.kt                 # 02: maf-to-gvcf command
│   └── utils/
│       ├── FileDownloader.kt            # Download and extraction utilities
│       ├── ProcessRunner.kt             # Process execution utilities
│       └── LoggingUtils.kt              # Centralized logging configuration
└── resources/
    ├── pixi.toml                        # Pixi environment configuration
    └── log4j2.xml                       # Logging configuration
```

### Build Commands

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

**Generate JAR:**
```bash
./gradlew jar
```

## Possible end-user errors...

**Issue**: `UnsupportedClassVersionError` when running maf-to-gvcf

**Solution**: Ensure you have Java 21 in your pixi environment. The command should run biokotlin-tools through `pixi run` automatically.

---

**Issue**: No MAF files generated by align-assemblies

**Solution**: Check that:
1. AnchorWave is available (Linux only)
2. Input files have correct extensions (`.fa`, `.fasta`, `.fna`)
3. Reference GFF and FASTA files are valid
4. Check `logs/01_align_assemblies.log` for detailed error messages

---

**Issue**: Permission denied when running commands

**Solution**: Ensure the working directory is writable and you have permissions to execute pixi and other tools.


