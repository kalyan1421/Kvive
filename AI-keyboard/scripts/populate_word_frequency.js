#!/usr/bin/env node

/**
 * Firestore Word Frequency Population Script
 * 
 * This script populates the dictionary_frequency collection in Firestore
 * with word frequency data for the autocorrect system.
 * 
 * Usage:
 *   1. Install Firebase Admin SDK: npm install firebase-admin
 *   2. Download service account key from Firebase Console
 *   3. Set GOOGLE_APPLICATION_CREDENTIALS environment variable
 *   4. Run: node populate_word_frequency.js
 * 
 * Data Format:
 *   Collection: dictionary_frequency
 *   Document: {language_code} (e.g., "en", "es", "fr")
 *   Fields: { "word": frequency_count, ... }
 * 
 * Frequency Sources:
 *   - Google Books Ngrams: https://books.google.com/ngrams
 *   - SUBTLEX: https://www.ugent.be/pp/experimentele-psychologie/en/research/documents/subtlexus
 *   - OpenSubtitles: https://opus.nlpl.eu/OpenSubtitles-v2018.php
 */

const admin = require('firebase-admin');
const fs = require('fs');

// Initialize Firebase Admin
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
});

const db = admin.firestore();

/**
 * Sample word frequency data (you should replace this with actual corpus data)
 * Format: word -> frequency count
 */
const SAMPLE_FREQUENCIES = {
  en: {
    // Most common English words with approximate frequencies
    the: 23135851162,
    be: 12545825682,
    and: 10741073461,
    of: 10343885338,
    a: 10144200227,
    in: 6996437293,
    to: 6332195741,
    have: 4303955663,
    it: 3872477313,
    i: 3978265900,
    that: 3430996323,
    for: 3281454912,
    you: 3081151854,
    he: 2909254890,
    with: 2683014854,
    on: 2485306748,
    do: 2573587163,
    say: 1915138923,
    this: 1885366841,
    they: 1864306818,
    at: 1767638089,
    but: 1776767265,
    we: 1820935302,
    his: 1801708598,
    from: 1635914073,
    not: 1619007684,
    by: 1490548984,
    she: 1484869074,
    or: 1379320838,
    as: 1296879502,
    what: 1181023488,
    go: 1151045541,
    their: 1083029172,
    can: 1022775684,
    who: 1018283768,
    get: 992596676,
    if: 987437185,
    would: 967934841,
    her: 969591494,
    all: 952432887,
    my: 919821415,
    make: 857168255,
    about: 836491448,
    know: 826629093,
    will: 824568834,
    up: 800820269,
    one: 768232444,
    time: 764657138,
    there: 746134220,
    year: 735456691,
    so: 731945169,
    think: 728262090,
    when: 673850979,
    which: 665542812,
    them: 632340914,
    some: 629456116,
    me: 625735316,
    people: 620935677,
    take: 600976983,
    out: 594593956,
    into: 594017856,
    just: 586441729,
    see: 578044984,
    him: 572328162,
    your: 569643369,
    come: 562675965,
    could: 561647142,
    now: 555499986,
    than: 552463711,
    like: 551664109,
    other: 546352215,
    how: 533353941,
    then: 531456753,
    its: 529424774,
    our: 525337582,
    two: 519634941,
    more: 512664473,
    these: 497163710,
    want: 493167395,
    way: 488240898,
    look: 487472891,
    first: 485470154,
    also: 480107981,
    new: 479702621,
    because: 467196214,
    day: 464774569,
    use: 455674775,
    no: 454487629,
    man: 453564486,
    find: 447503939,
    here: 438830429,
    thing: 432820190,
    give: 432677247,
    many: 431451758,
    well: 429456710,
    // Add more common words...
    love: 400000000,
    good: 380000000,
    work: 350000000,
    please: 120000000,
    thank: 110000000,
    thanks: 100000000,
    hello: 95000000,
    world: 90000000,
    // Common typos and their corrections
    teh: 50000, // intentionally low for typo
    hte: 30000,
    adn: 25000,
    taht: 20000,
  },
  es: {
    // Spanish frequency data (sample)
    el: 18000000000,
    la: 16000000000,
    de: 15000000000,
    que: 13000000000,
    y: 12000000000,
    a: 11000000000,
    en: 9000000000,
    un: 7000000000,
    ser: 6500000000,
    se: 6000000000,
    no: 5500000000,
    haber: 5000000000,
    por: 4800000000,
    con: 4500000000,
    su: 4200000000,
    para: 4000000000,
    como: 3800000000,
    estar: 3500000000,
    tener: 3200000000,
    le: 3000000000,
  },
  fr: {
    // French frequency data (sample)
    le: 20000000000,
    de: 18000000000,
    un: 14000000000,
    être: 12000000000,
    et: 11000000000,
    à: 10000000000,
    il: 9000000000,
    avoir: 8500000000,
    ne: 8000000000,
    je: 7500000000,
    son: 7000000000,
    que: 6500000000,
    se: 6000000000,
    qui: 5500000000,
    ce: 5200000000,
    dans: 5000000000,
    en: 4800000000,
    du: 4500000000,
    elle: 4200000000,
    au: 4000000000,
  },
  de: {
    // German frequency data (sample)
    der: 22000000000,
    die: 20000000000,
    und: 18000000000,
    in: 15000000000,
    den: 13000000000,
    von: 12000000000,
    zu: 11000000000,
    das: 10000000000,
    mit: 9000000000,
    sich: 8500000000,
    des: 8000000000,
    auf: 7500000000,
    für: 7000000000,
    ist: 6800000000,
    im: 6500000000,
    dem: 6200000000,
    nicht: 6000000000,
    ein: 5800000000,
    eine: 5500000000,
    als: 5200000000,
  },
  hi: {
    // Hindi frequency data (sample - Devanagari script)
    का: 15000000000,
    के: 14000000000,
    में: 13000000000,
    है: 12000000000,
    की: 11000000000,
    और: 10000000000,
    से: 9000000000,
    को: 8000000000,
    एक: 7500000000,
    पर: 7000000000,
    ने: 6500000000,
    यह: 6000000000,
    कि: 5800000000,
    हैं: 5500000000,
    था: 5200000000,
    गया: 5000000000,
    करने: 4800000000,
    लिए: 4500000000,
    भी: 4200000000,
    जा: 4000000000,
  }
};

/**
 * Upload frequency data for a specific language
 */
async function uploadLanguageFrequency(language, frequencies) {
  console.log(`📤 Uploading ${Object.keys(frequencies).length} words for ${language}...`);
  
  try {
    const docRef = db.collection('dictionary_frequency').doc(language);
    
    // Firestore has a limit of ~1MB per document
    // For larger datasets, you might need to batch or use subcollections
    await docRef.set(frequencies, { merge: true });
    
    console.log(`✅ Successfully uploaded frequency data for ${language}`);
  } catch (error) {
    console.error(`❌ Error uploading ${language}:`, error);
  }
}

/**
 * Load frequency data from file (alternative to hardcoded data)
 */
function loadFrequencyFromFile(filepath) {
  try {
    const data = fs.readFileSync(filepath, 'utf8');
    // Support formats: JSON, TSV (word\tfrequency), CSV
    
    if (filepath.endsWith('.json')) {
      return JSON.parse(data);
    } else if (filepath.endsWith('.tsv') || filepath.endsWith('.txt')) {
      const frequencies = {};
      const lines = data.split('\n');
      
      for (const line of lines) {
        const [word, freq] = line.split('\t');
        if (word && freq) {
          frequencies[word.trim()] = parseInt(freq);
        }
      }
      
      return frequencies;
    }
    
    throw new Error('Unsupported file format. Use .json or .tsv');
  } catch (error) {
    console.error(`Error loading file ${filepath}:`, error.message);
    return null;
  }
}

/**
 * Main execution
 */
async function main() {
  console.log('🚀 Starting Firestore word frequency population...\n');
  
  // Check if custom data files are provided
  const args = process.argv.slice(2);
  
  if (args.length > 0) {
    // Load from files: node script.js en:/path/to/en.tsv es:/path/to/es.json
    for (const arg of args) {
      const [lang, filepath] = arg.split(':');
      if (lang && filepath) {
        console.log(`Loading ${lang} from ${filepath}`);
        const frequencies = loadFrequencyFromFile(filepath);
        
        if (frequencies) {
          await uploadLanguageFrequency(lang, frequencies);
        }
      }
    }
  } else {
    // Use sample data
    console.log('Using sample frequency data (replace with actual corpus data)');
    console.log('Tip: Use "node script.js en:./english_freq.tsv es:./spanish_freq.json"\n');
    
    for (const [lang, frequencies] of Object.entries(SAMPLE_FREQUENCIES)) {
      await uploadLanguageFrequency(lang, frequencies);
    }
  }
  
  console.log('\n✨ Word frequency population complete!');
  console.log('\n📝 Next steps:');
  console.log('  1. Deploy Firestore rules: firebase deploy --only firestore:rules');
  console.log('  2. Verify data in Firebase Console');
  console.log('  3. Restart your keyboard app to load new frequencies');
  
  process.exit(0);
}

// Run the script
main().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});

