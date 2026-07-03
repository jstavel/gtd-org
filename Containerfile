FROM mcr.microsoft.com/playwright:v1.49.0-noble

WORKDIR /app

# 1. Nejdřív jen závislosti (využije cache Dockeru)
COPY package.json package-lock.json ./
RUN npm ci

# 2. Až potom zbytek kódu
COPY out/ ./out/

# 3. Příprava datového adresáře s právy pro uživatele pwuser
RUN mkdir -p /app/data && chown -R pwuser:pwuser /app/data

# Přepnutí na ne-root uživatele (lepší bezpečnost)
USER pwuser

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV NODE_ENV=production

ENTRYPOINT ["npx"]
CMD ["--help"]
