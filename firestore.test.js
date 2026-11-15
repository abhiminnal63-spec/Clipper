const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-clipper-3c803001-7045-4d46-8dc8-75ede099af4e";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    sourceType: "youtube",
    videoId: "dQw4w9WgXcQ",
    sourceUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    title: "How to Build Viral Shorts",
    channelName: "Creator Studio",
    thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    durationSeconds: 420,
    aspectRatio: "9:16",
    status: "preview",
    strategyNotes: "",
    clips: [],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or write projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1").set(
      validProjectPayload(ALICE_UID)
    )
  );
});

test("Authenticated user can create, list, update, and delete own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertSucceeds(
    docRef.update({
      status: "analyzed",
      clips: [
        {
          clipId: "c1",
          title: "Strongest Hook",
          startSeconds: 12,
          endSeconds: 42,
          hookScore: 92,
          rationale: "High energy intro",
          hashtags: ["#shorts"],
        },
      ],
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Authenticated user cannot read or write another user's project", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID)
      .collection("users").doc(BOB_UID).collection("projects").doc("bob_proj")
      .set(validProjectPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("bob_proj").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").doc("alice_inject").set(
      validProjectPayload(ALICE_UID)
    )
  );
});

test("Shadow update with ghost field or immutable createdAt mutation is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_2");

  await assertFails(
    docRef.set(validProjectPayload(ALICE_UID, { isAdmin: true }))
  );

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 100000),
      updatedAt: new Date(),
    })
  );
});

test("Invalid document ID or oversized title is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("bad id!").set(
      validProjectPayload(ALICE_UID)
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_long").set(
      validProjectPayload(ALICE_UID, { title: "a".repeat(250) })
    )
  );
});
