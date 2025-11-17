#!/bin/bash

# Setup script for OpenAI Backend Proxy
# This script helps you configure the Firebase Function with your OpenAI API key

echo "🔧 Setting up OpenAI Backend Proxy..."
echo ""

# Get API key from user
read -p "Enter your OpenAI API key (sk-proj-...): " API_KEY

if [ -z "$API_KEY" ]; then
    echo "❌ Error: API key cannot be empty"
    exit 1
fi

echo ""
echo "📦 Installing dependencies..."
cd functions
npm install

echo ""
echo "🔐 Setting environment variable..."
# For Firebase Functions v2, we'll use secrets
firebase functions:secrets:set OPENAI_API_KEY <<< "$API_KEY"

if [ $? -eq 0 ]; then
    echo "✅ API key configured successfully!"
    echo ""
    echo "🚀 Deploying Firebase Function..."
    firebase deploy --only functions:openaiChat
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Setup complete! Your OpenAI proxy is now ready."
        echo ""
        echo "📝 Next steps:"
        echo "   1. Test the function in your app"
        echo "   2. Monitor usage in Firebase Console"
        echo "   3. Check OpenAI dashboard for API usage"
    else
        echo "❌ Deployment failed. Check the error messages above."
        exit 1
    fi
else
    echo "❌ Failed to set API key. Make sure you're logged in to Firebase."
    echo "   Run: firebase login"
    exit 1
fi

