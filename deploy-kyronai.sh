#!/bin/bash
# deploy-kyronai.sh
# Copia todos os arquivos novos para o projeto Kyron AI
# Execute a partir da raiz do projeto: bash deploy-kyronai.sh

set -e

OUTPUTS="$(dirname "$0")"  # diretório onde este script está
PROJECT="$(pwd)"            # raiz do projeto kyron-ai

echo "📂 Projeto: $PROJECT"
echo "📦 Origem:  $OUTPUTS"
echo ""

# ── Modelo ──────────────────────────────────────────────────────
MODEL_DIR="$PROJECT/backend/src/main/java/otavio/kyronai/model"
echo "→ Copiando models..."
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/AgentAction.java"        "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/AgentActionDTO.java"     "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/CodeSession.java"        "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/CodeSessionDTO.java"     "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/GeneratedFile.java"      "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/GitHubRepository.java"   "$MODEL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/model/GitHubRepositoryDTO.java" "$MODEL_DIR/"

# ── Repositórios ─────────────────────────────────────────────────
REPO_DIR="$PROJECT/backend/src/main/java/otavio/kyronai/repository"
echo "→ Copiando repositories..."
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/repository/AgentActionRepository.java"     "$REPO_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/repository/CodeSessionRepository.java"     "$REPO_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/repository/GeneratedFileRepository.java"   "$REPO_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/repository/GitHubRepositoryRepository.java" "$REPO_DIR/"

# ── Serviços ─────────────────────────────────────────────────────
SVC_DIR="$PROJECT/backend/src/main/java/otavio/kyronai/service"
echo "→ Copiando services..."
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/service/AgentService.java"          "$SVC_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/service/CodeGenerationService.java" "$SVC_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/service/GitHubService.java"         "$SVC_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/service/OllamaService.java"         "$SVC_DIR/"

# ── Controllers ──────────────────────────────────────────────────
CTL_DIR="$PROJECT/backend/src/main/java/otavio/kyronai/controller"
echo "→ Copiando controllers..."
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/controller/AgentController.java"   "$CTL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/controller/ChatController.java"    "$CTL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/controller/CodeController.java"    "$CTL_DIR/"
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/controller/GitHubController.java"  "$CTL_DIR/"

# ── Config ───────────────────────────────────────────────────────
CFG_DIR="$PROJECT/backend/src/main/java/otavio/kyronai/config"
echo "→ Copiando config..."
cp "$OUTPUTS/backend/src/main/java/otavio/kyronai/config/CorsConfig.java" "$CFG_DIR/"

# ── Frontend estático ────────────────────────────────────────────
STATIC_DIR="$PROJECT/backend/src/main/resources/static"
echo "→ Copiando frontend..."
cp "$OUTPUTS/backend/src/main/resources/static/app.js"    "$STATIC_DIR/"
cp "$OUTPUTS/backend/src/main/resources/static/index.html" "$STATIC_DIR/"
cp "$OUTPUTS/backend/src/main/resources/static/style.css"  "$STATIC_DIR/"

echo ""
echo "✅ Todos os arquivos copiados com sucesso!"
echo ""
echo "Agora execute:"
echo "  docker compose down && docker compose up -d --build"