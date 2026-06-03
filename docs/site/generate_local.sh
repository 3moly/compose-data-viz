#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

echo "Starting local docs generation..."

# 1. Ensure we are running the script from the docs/site directory
# (This allows you to run the script from anywhere in your terminal)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
cd "$SCRIPT_DIR"

echo "Working directory: $SCRIPT_DIR"

# 2. Create Python virtual environment if it doesn't exist
if [ ! -d ".venv" ]; then
    echo "Creating new Python virtual environment..."
    python3 -m venv .venv
fi

# 3. Activate the virtual environment
echo "Activating virtual environment..."
source .venv/bin/activate

# 4. Install or update dependencies
echo "Installing dependencies..."
pip install -r mkdocs-requirements.txt

# 5. Build or Serve based on user input
if [[ "$1" == "--serve" || "$1" == "-s" ]]; then
    echo "Starting local development server..."
    # mkdocs/zensical serve provides a local web server with live-reloading
    zensical serve
else
    echo "Building static HTML site..."
    # Clean old builds and generate new site using the correct flag
    zensical build --clean

    echo "------------------------------------------------------"
    echo "✅ Build complete!"
    echo "📁 The generated HTML is located in: docs/site/site/"
    echo "💡 Tip: To preview the site live, run: ./generate_local.sh --serve"
    echo "------------------------------------------------------"
fi