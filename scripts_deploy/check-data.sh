#!/bin/bash
# Script de consultation des données MySQL Docker

echo "🔍 Consultation Base de Données io.law"
echo "========================================"
echo ""

# Vérifier que les conteneurs tournent
if ! docker ps | grep -q law-mysql; then
    echo "❌ Conteneur law-mysql non démarré"
    echo "💡 Lancez: docker compose up -d"
    exit 1
fi

# Mot de passe depuis .env ou défaut
DB_PASSWORD=${MYSQL_ROOT_PASSWORD:-law_password}

echo "📊 1. DOCUMENTS PAR STATUT"
echo "─────────────────────────"
docker exec law-mysql mysql -u root -p${DB_PASSWORD} law_db -se "
SELECT status, COUNT(*) as nombre 
FROM law_documents 
GROUP BY status 
ORDER BY nombre DESC;
" 2>/dev/null

echo ""
echo "📄 2. DERNIERS DOCUMENTS (top 10)"
echo "──────────────────────────────────"
docker exec law-mysql mysql -u root -p${DB_PASSWORD} law_db -se "
SELECT 
  CONCAT(type, '-', document_year, '-', number) as document,
  status
FROM law_documents 
ORDER BY document_year DESC, number DESC
LIMIT 10;
" 2>/dev/null

echo ""
echo "✅ 3. CONSOLIDATION"
echo "───────────────────"
docker exec law-mysql mysql -u root -p${DB_PASSWORD} law_db -se "
SELECT 
  (SELECT COUNT(*) FROM consolidated_metadata) as docs_consolides,
  (SELECT COUNT(*) FROM consolidated_articles) as articles,
  (SELECT COUNT(*) FROM consolidated_signatories) as signataires;
" 2>/dev/null

echo ""
echo "🔄 4. JOBS EN COURS/RECENTS"
echo "───────────────────────────"
docker exec law-mysql mysql -u root -p${DB_PASSWORD} law_db -se "
SELECT 
  j.JOB_NAME as job,
  e.STATUS as statut,
  DATE_FORMAT(e.START_TIME, '%Y-%m-%d %H:%i') as debut,
  CASE 
    WHEN e.END_TIME IS NULL THEN 'EN_COURS'
    ELSE DATE_FORMAT(e.END_TIME, '%Y-%m-%d %H:%i')
  END as fin,
  CASE
    WHEN e.END_TIME IS NULL THEN CONCAT(TIMESTAMPDIFF(SECOND, e.START_TIME, NOW()), 's (en cours)')
    ELSE CONCAT(TIMESTAMPDIFF(SECOND, e.START_TIME, e.END_TIME), 's')
  END as duree
FROM BATCH_JOB_INSTANCE j
LEFT JOIN BATCH_JOB_EXECUTION e ON j.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID
ORDER BY e.JOB_EXECUTION_ID DESC
LIMIT 5;
" 2>/dev/null

echo ""
echo "📈 5. PROGRESSION GLOBALE"
echo "─────────────────────────"
docker exec law-mysql mysql -u root -p${DB_PASSWORD} law_db -se "
SELECT 
  COUNT(*) as total_documents,
  SUM(CASE WHEN status = 'FETCHED' THEN 1 ELSE 0 END) as a_telecharger,
  SUM(CASE WHEN status = 'DOWNLOADED' THEN 1 ELSE 0 END) as a_extraire,
  SUM(CASE WHEN status = 'EXTRACTED' THEN 1 ELSE 0 END) as a_consolider,
  SUM(CASE WHEN status = 'CONSOLIDATED' THEN 1 ELSE 0 END) as consolides,
  SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as echecs
FROM law_documents;
" 2>/dev/null

echo ""
echo "✨ Terminé !"
