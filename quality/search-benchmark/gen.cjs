// Benchmark data generator: the SAME rows for MySQL (CSV) and Elasticsearch (NDJSON bulk files).
// Run:  node gen.cjs   (writes into ./data)
const fs = require('fs');
const path = require('path');
const out = path.join(__dirname, 'data');
fs.mkdirSync(out, { recursive: true });

function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const R = rng(42);
const pick = (list) => list[Math.floor(R() * list.length)];

const ADJ = 'Spice Golden Royal Fresh Tasty Urban Rustic Smoky Sunny Crispy Zesty Hearty Silver Little Grand Happy Lucky Green Red Blue Saffron Masala Tandoori Coastal Himalayan Mughal Punjabi Bengal Chennai Delhi Mumbai Kerala Goan Hyderabad Lucknow Amritsari Malabar Rajwada Desi Classic Original Famous Secret Midnight Morning Evening Street Village Garden Palace Corner Home Family Fusion Organic Healthy Express Gourmet Artisan Wok Grill Tawa'.split(' ');
const NOUN = 'Kitchen Biryani Dhaba Cafe Bistro Tiffin Bakery Pizzeria Diner Eatery Curry Tandoor Chaat Sweets Roll Wrap Burger Noodle Dosa Thali Mess Hub Bowl Plate Spoon Fork House Table Oven Pot Tava Cart Court Lounge Grill Barbeque Juice Shake'.split(' ');
const SUFFIX = ['', '', '', 'Express', 'Junction', 'Point', 'Corner', 'Express Kitchen', 'and Co', 'Downtown', 'Central', 'Prime'];
const CUISINES = ['North Indian', 'South Indian', 'Chinese', 'Italian', 'Biryani', 'Desserts', 'Beverages', 'Fast Food', 'Mughlai', 'Seafood', 'Pizza', 'Burgers'];
const CITIES = ['Bengaluru', 'Mysuru', 'Mumbai', 'Pune', 'Delhi', 'Hyderabad', 'Chennai', 'Kolkata', 'Jaipur', 'Lucknow', 'Kochi', 'Goa', 'Surat', 'Indore', 'Nagpur', 'Patna', 'Bhopal', 'Chandigarh', 'Noida', 'Gurugram', 'Thane', 'Nashik', 'Vizag', 'Madurai', 'Coimbatore', 'Mangaluru', 'Udaipur', 'Agra', 'Varanasi', 'Ranchi', 'Dehradun', 'Shimla', 'Amritsar', 'Ludhiana', 'Rajkot', 'Vadodara', 'Guwahati', 'Bhubaneswar', 'Raipur', 'Mohali'];
const STATUS = ['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'PENDING'];
const FIRST = 'Priya Rahul Anil Sunita Kavya Arjun Meera Rohan Neha Vikram Asha Ravi Farah Gita Hari Chitra Imran Deepa Manoj Lata Sanjay Pooja Karan Anita Suresh Divya Nikhil Rekha Amit Swati Rajesh Nisha Varun Isha Mohan Tara Kunal Ritu Sachin Alka'.split(' ');
const LAST = 'Sharma Nair Rao Iyer Patel Singh Kumar Reddy Gupta Mehta Joshi Das Menon Pillai Khan Shah Verma Bose Chopra Kapoor Malhotra Bhatt Desai Kulkarni Naidu Hegde Shetty Pandey Mishra Sinha Ghosh Mukherjee Banerjee Saxena Agarwal Jain Thakur Yadav Chauhan Tiwari'.split(' ');
const ROLES = ['CUSTOMER', 'CUSTOMER', 'CUSTOMER', 'CUSTOMER', 'CUSTOMER', 'CUSTOMER', 'RESTAURANT_ADMIN', 'DELIVERY_PARTNER'];

const iso = (ms) => new Date(ms).toISOString().replace('T', ' ').replace('Z', '');
const T0 = Date.parse('2024-10-01T00:00:00Z');
const SPAN = 2 * 365 * 24 * 3600 * 1000;

function bulk(file, docs, index) {
  const lines = [];
  for (const doc of docs) {
    lines.push(JSON.stringify({ index: { _index: index, _id: String(doc.id) } }));
    lines.push(JSON.stringify(doc));
  }
  fs.writeFileSync(file, lines.join('\n') + '\n');
}

// ---- restaurants
const N_REST = 300_000;
const restCsv = fs.createWriteStream(path.join(out, 'restaurants.csv'));
let chunk = [];
let part = 0;
for (let id = 1; id <= N_REST; id++) {
  const name = [pick(ADJ), pick(NOUN), pick(SUFFIX)].filter(Boolean).join(' ');
  const cuisines = [...new Set([pick(CUISINES), R() < 0.5 ? pick(CUISINES) : null, R() < 0.2 ? pick(CUISINES) : null].filter(Boolean))];
  const city = pick(CITIES);
  const status = pick(STATUS);
  const created = T0 + Math.floor(R() * SPAN);
  const lat = 8 + R() * 28;
  const lon = 68 + R() * 29;
  restCsv.write([id, 1 + Math.floor(R() * 50000), name, cuisines.join(','), city, status, iso(created), `${name} Pvt Ltd`, `Serving ${cuisines.join(' and ')} since 20${10 + Math.floor(R() * 14)}`, lat.toFixed(6), lon.toFixed(6)].join('\t') + '\n');
  chunk.push({
    id, name, description: `Serving ${cuisines.join(' and ')}`, cuisines, city, location: { lat: +lat.toFixed(6), lon: +lon.toFixed(6) }, status, sourceVersion: 0,
    indexedAt: new Date().toISOString(), createdAt: iso(created).replace(' ', 'T') + 'Z',
  });
  if (chunk.length === 25_000) {
    bulk(path.join(out, `restaurant-${String(part++).padStart(2, '0')}.ndjson`), chunk, 'bench_restaurant');
    chunk = [];
  }
}
restCsv.end();
if (chunk.length) bulk(path.join(out, `restaurant-${String(part++).padStart(2, '0')}.ndjson`), chunk, 'bench_restaurant');

// ---- users
const N_USER = 500_000;
const userCsv = fs.createWriteStream(path.join(out, 'users.csv'));
chunk = [];
part = 0;
for (let id = 1; id <= N_USER; id++) {
  const first = pick(FIRST);
  const last = pick(LAST);
  const fullName = `${first} ${last}`;
  const email = `${first}.${last}.${id}@example.com`.toLowerCase();
  const phone = `+91${String(6000000000 + id * 7).slice(0, 10)}`;
  const role = pick(ROLES);
  const status = R() < 0.93 ? 'ACTIVE' : 'INACTIVE';
  const created = T0 + Math.floor(R() * SPAN);
  userCsv.write([id, email, 'x', fullName, phone, role, status, iso(created), iso(created)].join('\t') + '\n');
  chunk.push({ id, fullName, email, phone, role, status, createdAt: iso(created).replace(' ', 'T') + 'Z' });
  if (chunk.length === 25_000) {
    bulk(path.join(out, `user-${String(part++).padStart(2, '0')}.ndjson`), chunk, 'bench_user');
    chunk = [];
  }
}
userCsv.end();
if (chunk.length) bulk(path.join(out, `user-${String(part++).padStart(2, '0')}.ndjson`), chunk, 'bench_user');
console.log('generated', N_REST, 'restaurants and', N_USER, 'users');
