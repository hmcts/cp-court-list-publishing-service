#!/bin/bash
# Deploy by copying a JAR into running pods and restarting them.
#
# NOTE: This approach fails when the container image has the Spring Boot app
# as its entrypoint (e.g. CMD ["java", "-jar", "/opt/app/app.jar"]). Replacing
# the JAR in the running container and then deleting the pod to "restart" does
# not persist the new JAR: the new pod starts from the same image again, so it
# runs the original baked-in JAR. The copied file is only in the old pod's
# filesystem. For JAR-based deploy to work, the image would need to run a
# wrapper that executes whatever JAR is present at a path (e.g. a volume or
# copied path) so that updates take effect after a restart without reverting
# to the image's built-in JAR.

NOOP=false
VERIFY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --noop)   NOOP=true; shift ;;
    --verify) VERIFY=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--noop] [--verify] <jarfile_name> <namespace>"
      echo "  --noop    Dry run: show what would be done without making changes"
      echo "  --verify  After copy, verify JAR in pod matches local file (checksum)"
      echo ""
      echo "Actuator info (version) is called before and after deploy. Default URL (namespace: strip leading ns-, remove all dashes):"
      echo "  https://<processed-namespace>.ingress01.dev.nl.cjscp.org.uk/courtlistpublishing-service/actuator/info"
      echo "  Override with: ACTUATOR_URL=<url> $0 ..."
      exit 0
      ;;
    *) break ;;
  esac
done

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 [--noop] [--verify] <jarfile_name> <namespace>"
    exit 1
fi

JARFILE_NAME=$1
NAMESPACE=$2

# Derive service name from JAR filename only (strip path, version and .jar)
JAR_BASENAME=$(basename "$JARFILE_NAME")
SERVICE_NAME=$(echo "$JAR_BASENAME" | sed -E 's/-[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9]+)*\.jar$//')

# Pre-process: cp-court-list-publishing-service → courtlistpublishing-service (strip cp-, collapse hyphens, keep -service/-api)
if [[ "$SERVICE_NAME" == cp-* ]]; then
  SERVICE_NAME=$(echo "$SERVICE_NAME" | sed 's/^cp-//')
  SUFFIX=""
  [[ "$SERVICE_NAME" == *-service ]] && SUFFIX="-service" && SERVICE_NAME="${SERVICE_NAME%-service}"
  [[ "$SERVICE_NAME" == *-api ]] && SUFFIX="-api" && SERVICE_NAME="${SERVICE_NAME%-api}"
  SERVICE_NAME=$(echo "$SERVICE_NAME" | tr -d '-')${SUFFIX}
fi

if $NOOP; then
  echo "[NOOP] Dry run - no changes will be made."
  echo ""
fi

echo "Service name: $SERVICE_NAME"
CONTEXT=$(echo "$SERVICE_NAME" | sed -E 's/(-service|-api)$//')
echo "Context: $CONTEXT"
DEPLOYMENT_LABEL="app=$CONTEXT"
echo "Deployment label: $DEPLOYMENT_LABEL"

# Actuator host: namespace with leading 'ns-' removed and all dashes trimmed (e.g. ns-steccm62 -> steccm62)
ACTUATOR_NAMESPACE="${NAMESPACE#ns-}"
ACTUATOR_NAMESPACE="${ACTUATOR_NAMESPACE//-/}"
# Actuator info URL for version (override with ACTUATOR_URL env)
ACTUATOR_URL="${ACTUATOR_URL:-https://${ACTUATOR_NAMESPACE}.ingress01.dev.nl.cjscp.org.uk/courtlistpublishing-service/actuator/info}"

# JAR file location in container - check both possible locations
# Root Dockerfile uses /opt/app/app.jar
# docker/Dockerfile uses /app/ with pattern matching
JAR_PATH="/opt/app/app.jar"
ALT_JAR_PATH="/app"

echo "Trying to find deployment for service: $SERVICE_NAME in namespace: $NAMESPACE..."
if [[ "$SERVICE_NAME" == *-api ]]; then
  BASE_NAME=$(echo "$SERVICE_NAME" | sed 's/-api$//')
  echo "Base name: $BASE_NAME"
  DEPLOYMENT_NAME="${BASE_NAME}-service"
  echo "Deployment name: $DEPLOYMENT_NAME"
else
  DEPLOYMENT_NAME=$(kubectl get deployments -n "$NAMESPACE" -o name | grep "$SERVICE_NAME" | sed 's@.*/@@' | head -n1)
  echo "Deployment name: $DEPLOYMENT_NAME"
fi

if [ -z "$DEPLOYMENT_NAME" ]; then
    echo "Deployment not found for service: $SERVICE_NAME"
    exit 1
fi

echo "Found deployment: $DEPLOYMENT_NAME"

# Actuator info (version) check (before)
echo ""
echo "Actuator info (before deploy): $ACTUATOR_URL"
if $NOOP; then
  echo "[NOOP] Would run: curl -k -s -i -X GET $ACTUATOR_URL"
else
  curl -k -s -i -X GET "$ACTUATOR_URL" || true
fi
echo ""

# Backup deployment config
echo "Backing up deployment configuration..."
if $NOOP; then
  echo "[NOOP] Would run: kubectl get deployment $DEPLOYMENT_NAME -n $NAMESPACE -o json > current_deployment_backup_${DEPLOYMENT_NAME}.json"
else
  kubectl get deployment "$DEPLOYMENT_NAME" -n "$NAMESPACE" -o json > current_deployment_backup_"$DEPLOYMENT_NAME".json
fi

# Patch livenessProbe (similar to WAR script)
echo "Applying livenessProbe patch..."
if $NOOP; then
  echo "[NOOP] Would run: kubectl patch deployment $DEPLOYMENT_NAME -n $NAMESPACE (livenessProbe periodSeconds=3600, failureThreshold=100)"
else
  kubectl patch deployment "$DEPLOYMENT_NAME" -n "$NAMESPACE" --type='json' -p='[
      {"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/periodSeconds", "value": 3600},
      {"op": "replace", "path": "/spec/template/spec/containers/0/livenessProbe/failureThreshold", "value": 100}
  ]' 2>/dev/null || echo "Note: Could not patch livenessProbe (may not exist or already configured)"
fi
echo "Patch applied."

### SCALE DOWN ###
echo "Scaling down deployment to 0..."
if $NOOP; then
  echo "[NOOP] Would run: kubectl scale deployment $DEPLOYMENT_NAME --replicas=0 -n $NAMESPACE"
  echo "[NOOP] Would run: kubectl rollout status ..."
else
  kubectl scale deployment "$DEPLOYMENT_NAME" --replicas=0 -n "$NAMESPACE"
  kubectl rollout status deployment "$DEPLOYMENT_NAME" -n "$NAMESPACE" --timeout=120s
fi

### SCALE UP ###
echo "Scaling up deployment to 1..."
if $NOOP; then
  echo "[NOOP] Would run: kubectl scale deployment $DEPLOYMENT_NAME --replicas=1 -n $NAMESPACE"
  echo "[NOOP] Would wait for pod ready, then continue..."
  POD_NAMES="<pod-would-be-created>"
else
  kubectl scale deployment "$DEPLOYMENT_NAME" --replicas=1 -n "$NAMESPACE"

  echo "Waiting for pod to be ready (1/1)..."
  until [[ "$(kubectl get deployment "$DEPLOYMENT_NAME" -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}')" == "1" ]]; do
    echo "Still waiting for pod...next poll in 10 seconds..."
    sleep 10
  done
  echo "Deployment is ready."

  POD_NAMES=$(kubectl get pods -n "$NAMESPACE" -l "$DEPLOYMENT_LABEL" -o jsonpath="{.items[*].metadata.name}")
  if [ -z "$POD_NAMES" ]; then
    # Fallback: try to get pod by deployment name
    POD_NAMES=$(kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$CONTEXT" -o jsonpath="{.items[*].metadata.name}")
  fi
  if [ -z "$POD_NAMES" ]; then
    # Last resort: get pod directly from deployment
    POD_NAMES=$(kubectl get pods -n "$NAMESPACE" -o jsonpath="{.items[?(@.metadata.ownerReferences[0].name=='$DEPLOYMENT_NAME')].metadata.name}")
  fi

  if [ -z "$POD_NAMES" ]; then
    echo "Warning: Could not find pods with label $DEPLOYMENT_LABEL. Trying to get pod from deployment..."
    POD_NAMES=$(kubectl get pods -n "$NAMESPACE" | grep "$DEPLOYMENT_NAME" | awk '{print $1}' | head -n1)
  fi

  if [ -z "$POD_NAMES" ]; then
    echo "Error: Could not find any pods for deployment $DEPLOYMENT_NAME"
    exit 1
  fi
fi

echo "Pods: $POD_NAMES"

for POD_NAME in $POD_NAMES; do
  echo "Checking JAR location in pod: $POD_NAME"

  if $NOOP; then
    JAR_DEST="/opt/app/app.jar"
    echo "[NOOP] Would detect path (assuming $JAR_DEST or /app)"
  else
    # Check which path exists in the container
    if kubectl exec -n "$NAMESPACE" "$POD_NAME" -- test -d /opt/app 2>/dev/null; then
      JAR_DEST="/opt/app/app.jar"
      echo "Using path: $JAR_DEST"
    elif kubectl exec -n "$NAMESPACE" "$POD_NAME" -- test -d /app 2>/dev/null; then
      JAR_DEST="/app"
      echo "Using path: $JAR_DEST (will copy to directory, container will pick up JAR)"
    else
      echo "Warning: Neither /opt/app nor /app directory found. Trying /opt/app/app.jar..."
      JAR_DEST="/opt/app/app.jar"
    fi
  fi

  # Remove old JAR files if they exist
  echo "Removing old JAR files in pod: $POD_NAME"
  if $NOOP; then
    echo "[NOOP] Would run: kubectl exec ... rm -f (in $JAR_DEST parent)"
  elif [ "$JAR_DEST" = "/app" ]; then
    kubectl exec -n "$NAMESPACE" "$POD_NAME" -- sh -c "rm -f /app/*.jar" 2>/dev/null || true
  else
    kubectl exec -n "$NAMESPACE" "$POD_NAME" -- sh -c "rm -f /opt/app/*.jar" 2>/dev/null || true
  fi

  if ! $NOOP; then
    sleep 2
  fi

  # Copy new JAR file: copy to /tmp first (writable in most containers), then move into place
  echo "Copying JAR file to pod: $POD_NAME"
  if $NOOP; then
    echo "[NOOP] Would run: kubectl cp $JARFILE_NAME $NAMESPACE/$POD_NAME:$JAR_DEST"
  elif [ "$JAR_DEST" = "/app" ]; then
    kubectl cp "$JARFILE_NAME" "$NAMESPACE/$POD_NAME:/app/" || { echo "Error: Failed to copy JAR to pod $POD_NAME" >&2; exit 1; }
  else
    kubectl cp "$JARFILE_NAME" "$NAMESPACE/$POD_NAME:/tmp/app.jar.new" || { echo "Error: Failed to copy JAR to pod $POD_NAME" >&2; exit 1; }
    if ! kubectl exec -n "$NAMESPACE" "$POD_NAME" -- sh -c "mv /tmp/app.jar.new /opt/app/app.jar.new && rm -f /opt/app/app.jar && mv /opt/app/app.jar.new /opt/app/app.jar" 2>/dev/null; then
      echo "Note: /opt/app not writable, trying /app ..."
      if ! kubectl exec -n "$NAMESPACE" "$POD_NAME" -- sh -c "mv /tmp/app.jar.new /app/app.jar.new && rm -f /app/app.jar && mv /app/app.jar.new /app/app.jar" 2>/dev/null; then
        echo "Error: Failed to replace app.jar in pod $POD_NAME. Container user cannot write to /opt/app or /app." >&2
        echo "  Fix: use an image that runs as root, or has /opt/app (or /app) writable by the process user." >&2
        exit 1
      fi
      echo "JAR placed in /app (container may need to run from /app to use it)."
    fi
  fi

  echo "JAR file copied successfully to $POD_NAME"

  # Optional: verify JAR in pod matches local file (before pod is restarted)
  if $VERIFY && ! $NOOP; then
    LOCAL_SHA=$(sha256sum < "$JARFILE_NAME" 2>/dev/null | awk '{print $1}' || shasum -a 256 < "$JARFILE_NAME" 2>/dev/null | awk '{print $1}')
    if [ -n "$LOCAL_SHA" ]; then
      if [ "$JAR_DEST" = "/app" ]; then
        REMOTE_JAR="/app/$(basename "$JARFILE_NAME")"
      else
        REMOTE_JAR="/opt/app/app.jar"
      fi
      REMOTE_SHA=$(kubectl exec -n "$NAMESPACE" "$POD_NAME" -- sha256sum "$REMOTE_JAR" 2>/dev/null | awk '{print $1}')
      if [ -n "$REMOTE_SHA" ] && [ "$LOCAL_SHA" = "$REMOTE_SHA" ]; then
        echo "Verified: JAR in pod matches local file (sha256)."
      elif [ -n "$REMOTE_SHA" ]; then
        echo "Warning: JAR in pod does not match local file (sha256 differs)." >&2
      else
        echo "Note: Could not compute checksum in pod (sha256sum/cksum not available)." >&2
      fi
    fi
  fi

  # Restart the container to pick up the new JAR
  echo "Restarting pod to pick up new JAR: $POD_NAME"
  if $NOOP; then
    echo "[NOOP] Would run: kubectl delete pod $POD_NAME -n $NAMESPACE"
  else
    kubectl delete pod "$POD_NAME" -n "$NAMESPACE"
  fi
done

echo "Waiting for new pod to be ready..."
if $NOOP; then
  echo "[NOOP] Would run: sleep 10; kubectl rollout status deployment $DEPLOYMENT_NAME -n $NAMESPACE --timeout=300s"
else
  sleep 10
  kubectl rollout status deployment "$DEPLOYMENT_NAME" -n "$NAMESPACE" --timeout=300s || { echo "Error: Rollout did not complete successfully" >&2; exit 1; }
fi

echo -e "\nDeployment complete!"

# Actuator info (version) check (after)
echo ""
echo "Actuator info (after deploy): $ACTUATOR_URL"
if $NOOP; then
  echo "[NOOP] Would run: curl -k -s -i -X GET $ACTUATOR_URL"
else
  curl -k -s -i -X GET "$ACTUATOR_URL" || true
fi
echo ""

echo -e "You can check pod status using:\n  kubectl get pods -l $DEPLOYMENT_LABEL -n $NAMESPACE"
echo -e "To view logs:\n  kubectl logs -l $DEPLOYMENT_LABEL -n $NAMESPACE --tail=100"
echo -e "To forward the port:\n  kubectl port-forward deployment/$DEPLOYMENT_NAME 8082:8082 -n $NAMESPACE"
echo -e "To rollback, use:\n  kubectl rollout undo deployment/$DEPLOYMENT_NAME -n $NAMESPACE"
