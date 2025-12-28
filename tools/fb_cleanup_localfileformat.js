/*
 Firebase DB cleanup script to set localFileFormat to null (remove property) if the value is empty string or invalid.
 Usage:
   1) Set environment variable GOOGLE_APPLICATION_CREDENTIALS to service account path, or pass --serviceAccountKey <path>
   2) node tools/fb_cleanup_localfileformat.js

 This expects Realtime Database URL in the service account's project or you may specify DATABASE_URL env var.
*/

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

const argv = require('yargs')
  .option('serviceAccountKey', {
    alias: 'k',
    describe: 'Path to service account JSON key file',
    type: 'string'
  })
  .option('databaseUrl', {
    alias: 'd',
    describe: 'Firebase Realtime Database URL',
    type: 'string'
  })
  .help()
  .argv;

const keyPath = argv.serviceAccountKey || process.env.GOOGLE_APPLICATION_CREDENTIALS;
const databaseUrl = argv.databaseUrl || process.env.DATABASE_URL;

if (!keyPath) {
  console.error('No service account key provided. Set GOOGLE_APPLICATION_CREDENTIALS or pass --serviceAccountKey <path>.');
  process.exit(1);
}

if (!fs.existsSync(keyPath)) {
  console.error('Service account key not found at: ' + keyPath);
  process.exit(1);
}

const serviceAccount = require(path.resolve(keyPath));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: databaseUrl
});

const db = admin.database();
const libraryRef = db.ref('library');

const VALID_FORMATS = new Set(['PDF', 'EPUB', 'TXT', 'HTML']);

(async function cleanup() {
  try {
    const snapshot = await libraryRef.once('value');
    const updates = {};
    let changedCount = 0;

    snapshot.forEach(userSnap => {
      const uid = userSnap.key;
      userSnap.forEach(bookSnap => {
        const bookId = bookSnap.key;
        const localFileFormat = bookSnap.child('localFileFormat').val();
        if (localFileFormat === "" || localFileFormat === null) {
          // If empty string or null explicitly set, remove property for clarity
          const childPath = `${uid}/${bookId}/localFileFormat`;
          console.log(`Removing empty localFileFormat for ${childPath}`);
          updates[childPath] = null;
          changedCount++;
        } else if (typeof localFileFormat === 'string') {
          if (!VALID_FORMATS.has(localFileFormat)) {
            const childPath = `${uid}/${bookId}/localFileFormat`;
            console.log(`Removing invalid localFileFormat '${localFileFormat}' for ${childPath}`);
            updates[childPath] = null;
            changedCount++;
          }
        } else {
          // Non-string values — remove to avoid serialization issues
          if (localFileFormat !== undefined) {
            const childPath = `${uid}/${bookId}/localFileFormat`;
            console.log(`Removing non-string localFileFormat for ${childPath}`);
            updates[childPath] = null;
            changedCount++;
          }
        }
      });
    });

    if (changedCount > 0) {
      console.log(`Applying ${changedCount} updates...`);
      await libraryRef.update(updates);
      console.log('Updates applied.');
    } else {
      console.log('No updates necessary.');
    }

    process.exit(0);
  } catch (err) {
    console.error('Error during cleanup:', err);
    process.exit(1);
  }
})();
