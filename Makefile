SHELL := /bin/sh

# ==============================
# Project Docker Operations
# ==============================

DC = docker compose
BASE = -f docker-compose.yml
DEV = -f docker-compose.dev.yml
PROD = -f docker-compose.prod.yml
LOCAL = -f docker-compose.local.yml

# ==============================
# ENVIRONMENTS
# ==============================

dev:
	$(DC) $(BASE) $(DEV) up -d --build

prod:
	$(DC) $(BASE) $(PROD) up -d --build

local:
	$(DC) $(BASE) $(LOCAL) up -d --build

# ==============================
# BASIC OPERATIONS
# ==============================

up:
	$(DC) $(BASE) up -d

build:
	$(DC) $(BASE) build

down:
	$(DC) $(BASE) down

stop:
	$(DC) $(BASE) stop

start:
	$(DC) $(BASE) start

restart:
	$(DC) $(BASE) down
	$(DC) $(BASE) up -d --build

# ==============================
# LOGGING & DEBUGGING
# ==============================

logs:
	$(DC) $(BASE) logs -f

logs-dev:
	$(DC) $(BASE) $(DEV) logs -f

logs-prod:
	$(DC) $(BASE) $(PROD) logs -f

ps:
	$(DC) $(BASE) ps

exec:
	$(DC) $(BASE) exec api sh

# ==============================
# CLEAN & REBUILD
# ==============================

rebuild:
	$(DC) $(BASE) down --remove-orphans
	$(DC) $(BASE) up -d --build

rebuild-no-cache:
	$(DC) $(BASE) build --no-cache
	$(DC) $(BASE) up -d

reset:
	$(DC) $(BASE) down --rmi local --volumes --remove-orphans

clean:
	docker system prune -f

clean-all:
	docker system prune -a --volumes -f

# ==============================
# IMAGE OPERATIONS
# ==============================

images:
	docker images

pull:
	$(DC) $(BASE) pull

push:
	$(DC) $(BASE) push

# ==============================
# HEALTH CHECK
# ==============================

status:
	$(DC) $(BASE) ps

top:
	$(DC) $(BASE) top

stats:
	docker stats

# ==============================

.PHONY: dev prod local up build down stop start restart logs logs-dev logs-prod ps exec rebuild rebuild-no-cache reset clean clean-all images pull push status top stats
