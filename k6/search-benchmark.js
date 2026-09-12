// Performance benchmark for search-service (GET /search), matching the
// concurrency ramp and latency targets documented in README.md §15
// (Performance Strategy): 100 -> 250 -> 500 -> 1000+ concurrent users,
// p50/p95/p99, error rate, tracked per concurrency level.
//
// Usage:
//   k6 run k6/search-benchmark.js
//
// Common overrides (env vars, all optional):
//   BASE_URL        search-service base URL (default: http://localhost:8083,
//                   i.e. through the blue-green nginx proxy; point this at
//                   http://localhost:8091 / :8092 to benchmark one color
//                   directly, or http://localhost:8080 to go through gateway)
//   TENANTS         comma-separated tenant IDs (default: tenant-001,tenant-002,tenant-003)
//   SEED            'true' (default) seeds sample documents before the run,
//                   'false' skips seeding (use when the index is already populated)
//   DOCS_PER_TENANT documents to seed per tenant when SEED=true (default: 40)
//   GATEWAY_URL     used only for seeding via document-service (default: http://localhost:8080)
//   API_KEY         gateway API key, used only for seeding (default: assessment-key)
//   HOLD            seconds to hold each concurrency level at its peak (default: 30)
//
// Example: run only against search-service-green, skip seeding, longer hold:
//   BASE_URL=http://localhost:8092 SEED=false HOLD=60 k6 run k6/search-benchmark.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'assessment-key';
const TENANTS = (__ENV.TENANTS || 'tenant-001,tenant-002,tenant-003').split(',');
const SEED = (__ENV.SEED || 'true') === 'true';
const DOCS_PER_TENANT = parseInt(__ENV.DOCS_PER_TENANT || '40', 10);
const HOLD = __ENV.HOLD || '30s';
const RAMP_UP = __ENV.RAMP_UP || '15s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';

const resultsFoundRate = new Rate('results_found_rate');

const CATEGORIES = ['technology', 'finance', 'healthcare', 'legal', 'operations'];
const DEPARTMENTS = ['engineering', 'sales', 'marketing', 'support', 'research'];
const AUTHORS = ['khushal', 'asha', 'ravi', 'meera', 'vikram'];

const CONTENT_SENTENCES = [
  'Distributed systems use multiple independent services that communicate over a network for scalability.',
  'Kafka enables asynchronous event processing and decouples write traffic from search indexing.',
  'Elasticsearch provides fast full-text search and BM25 relevance ranking across large document sets.',
  'Redis stores distributed rate-limit state so gateway replicas share a consistent policy.',
  'PostgreSQL is the durable source of truth for transactional document metadata.',
  'Tenant isolation ensures search results never leak across tenant boundaries in a multi-tenant platform.',
  'The API gateway authenticates requests and propagates tenant context to downstream services.',
  'Caffeine caches repeated search queries locally to reduce latency for hot queries.',
  'Blue-green deployment behind nginx allows zero-downtime rollouts of the search service.',
  'Horizontal scaling of stateless services supports thousands of concurrent searches per second.',
];

// Terms chosen to match CONTENT_SENTENCES above, plus a few misspellings to
// exercise the fuzzy-match clause in SearchService.
const QUERY_TERMS = [
  'distributed systems',
  'kafka indexing',
  'elasticsearch relevance ranking',
  'redis rate limit',
  'postgresql source of truth',
  'tenant isolation',
  'api gateway',
  'caffeine cache',
  'blue-green deployment',
  'horizontal scaling',
  'search index',
  'full-text search',
  'distirbuted systems', // fuzzy: missing letter
  'elasticsarch',        // fuzzy: transposed letters
  'kaffka',               // fuzzy: doubled letter
];

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildDocument(index) {
  const sentence = CONTENT_SENTENCES[index % CONTENT_SENTENCES.length];
  return {
    title: `Benchmark Document ${index} - ${pick(CATEGORIES)}`,
    content: `${sentence} Document reference number ${index} for load testing purposes.`,
    metadata: {
      category: pick(CATEGORIES),
      department: pick(DEPARTMENTS),
      author: pick(AUTHORS),
    },
  };
}

function stage(target) {
  return [
    { duration: RAMP_UP, target },
    { duration: HOLD, target },
    { duration: RAMP_DOWN, target: 0 },
  ];
}

// Stages run sequentially (one concurrency level at a time), so each
// scenario's startTime is derived from the others' total duration rather
// than hardcoded, since RAMP_UP/HOLD/RAMP_DOWN are env-overridable.
function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
  if (!match) throw new Error(`Unsupported duration format: ${duration}`);
  const n = parseInt(match[1], 10);
  return { ms: n / 1000, s: n, m: n * 60, h: n * 3600 }[match[2]];
}

const STAGE_DURATION_SECONDS = toSeconds(RAMP_UP) + toSeconds(HOLD) + toSeconds(RAMP_DOWN);

function startTimeFor(stageIndex) {
  return `${Math.round(stageIndex * STAGE_DURATION_SECONDS)}s`;
}

export const options = {
  scenarios: {
    load_100: {
      executor: 'ramping-vus',
      exec: 'search',
      startVUs: 0,
      stages: stage(100),
      startTime: startTimeFor(0),
      tags: { level: '100' },
    },
    load_250: {
      executor: 'ramping-vus',
      exec: 'search',
      startVUs: 0,
      stages: stage(250),
      startTime: startTimeFor(1),
      tags: { level: '250' },
    },
    load_500: {
      executor: 'ramping-vus',
      exec: 'search',
      startVUs: 0,
      stages: stage(500),
      startTime: startTimeFor(2),
      tags: { level: '500' },
    },
    load_1000: {
      executor: 'ramping-vus',
      exec: 'search',
      startVUs: 0,
      stages: stage(1000),
      startTime: startTimeFor(3),
      tags: { level: '1000' },
    },
  },
  thresholds: {
    // Assessment target: sub-500ms p95 search latency, at every concurrency level.
    'http_req_duration{level:100}': ['p(95)<500'],
    'http_req_duration{level:250}': ['p(95)<500'],
    'http_req_duration{level:500}': ['p(95)<500'],
    'http_req_duration{level:1000}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  if (!SEED) {
    return { seeded: false };
  }

  let created = 0;
  for (const tenant of TENANTS) {
    for (let i = 0; i < DOCS_PER_TENANT; i++) {
      const res = http.post(
        `${GATEWAY_URL}/documents`,
        JSON.stringify(buildDocument(i)),
        {
          headers: {
            'Content-Type': 'application/json',
            'X-API-Key': API_KEY,
            'X-Tenant-Id': tenant,
          },
          tags: { name: 'seed_document' },
        }
      );
      if (res.status === 201) created++;
    }
  }

  // Indexing is asynchronous (document-service -> Kafka -> indexer-service ->
  // Elasticsearch); give the pipeline a moment to catch up before load starts.
  sleep(5);

  console.log(`setup: seeded ${created} documents across ${TENANTS.length} tenant(s)`);
  return { seeded: true, tenantCount: TENANTS.length, docsCreated: created };
}

export function search() {
  const tenant = pick(TENANTS);
  const query = pick(QUERY_TERMS);

  // k6's JS runtime (goja) has no URLSearchParams, so build the query string by hand.
  const queryParts = [`q=${encodeURIComponent(query)}`];
  if (Math.random() < 0.5) {
    queryParts.push(`facets=${encodeURIComponent(pick(['category', 'department']))}`);
  }
  if (Math.random() < 0.2) {
    queryParts.push(`filter=${encodeURIComponent('category:' + pick(CATEGORIES))}`);
  }

  const res = http.get(`${BASE_URL}/search?${queryParts.join('&')}`, {
    headers: {
      'X-Tenant-Id': tenant,
      'X-API-Key': API_KEY, // ignored by search-service directly; needed if BASE_URL points at the gateway
    },
    tags: { name: 'search' },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has results field': (r) => {
      try {
        return Array.isArray(JSON.parse(r.body).results);
      } catch (e) {
        return false;
      }
    },
  });

  if (ok) {
    try {
      resultsFoundRate.add(JSON.parse(res.body).results.length > 0);
    } catch (e) {
      // ignore parse failure for the rate metric; the check above already failed it
    }
  }

  sleep(Math.random() * 0.5 + 0.1);
}
