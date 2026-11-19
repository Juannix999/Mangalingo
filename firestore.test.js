const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_manga_123";
const BOB_UID = "bob_manga_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validHistoryPayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    sourceText: "大丈夫ですか",
    translatedText: "¿Estás bien?",
    reading: "daijoubu desu ka",
    sourceLang: "ja",
    targetLang: "es",
    wordCount: 2,
    hasTranslation: true,
    partOfSpeech: "Expresión",
    contextNote: "Pregunta amable común en diálogos.",
    mangaId: "a96676e5-8ae2-425e-b549-7f15dd34a68a",
    mangaTitle: "Komi-san wa, Comyushou desu.",
    chapterLabel: "Cap. 1",
    createdAt: now,
    updatedAt: now,
  };
}

function validSavedPayload(uid) {
  return {
    ...validHistoryPayload(uid),
    userNote: "Muy usada en escenas cotidianas",
    mastered: false,
  };
}

function validFavoritePayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    mangaId: "a96676e5-8ae2-425e-b549-7f15dd34a68a",
    title: "Neo-Shibuya Chronicles",
    coverUrl: "https://uploads.mangadex.org/covers/sample.jpg",
    preferredLang: "ja",
    lastChapterId: "chap-001",
    lastChapterTitle: "Cap. 1",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write any MangaLingo user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("search_history").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("search_history")
      .doc("hist_1")
      .set(validHistoryPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("saved_translations")
      .doc("saved_1")
      .set(validSavedPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorite_mangas")
      .doc("a96676e5-8ae2-425e-b549-7f15dd34a68a")
      .set(validFavoritePayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete search_history with Kotlin-aligned payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("search_history");

  await assertSucceeds(col.doc("hist_1").set(validHistoryPayload(ALICE_UID)));
  await assertSucceeds(col.orderBy("updatedAt", "desc").get());
  await assertSucceeds(
    col.doc("hist_1").update({
      translatedText: "¿Todo bien?",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId "id" ghost field
  await assertFails(
    col.doc("hist_2").set({
      ...validHistoryPayload(ALICE_UID),
      id: "hist_2",
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    col.doc("hist_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject future timestamp
  await assertFails(
    col.doc("hist_3").set({
      ...validHistoryPayload(ALICE_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(col.doc("hist_1").delete());
});

test("Owner can create, update userNote/mastered, and delete saved_translations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("saved_translations");

  await assertSucceeds(col.doc("saved_1").set(validSavedPayload(ALICE_UID)));
  await assertSucceeds(col.orderBy("createdAt", "desc").get());
  await assertSucceeds(
    col.doc("saved_1").update({
      userNote: "Repasado hoy",
      mastered: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject changing immutable sourceText on saved translation
  await assertFails(
    col.doc("saved_1").update({
      sourceText: "Otro texto",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(col.doc("saved_1").delete());
});

test("Owner can manage favorite_mangas including UUID document IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("favorite_mangas");
  const uuid = "a96676e5-8ae2-425e-b549-7f15dd34a68a";

  await assertSucceeds(col.doc(uuid).set(validFavoritePayload(ALICE_UID)));
  await assertSucceeds(col.orderBy("updatedAt", "desc").get());
  await assertSucceeds(
    col.doc(uuid).update({
      lastChapterId: "chap-002",
      lastChapterTitle: "Cap. 2",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(col.doc(uuid).delete());
});

test("Cross-user access is strictly denied across all subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bob = ctx.firestore().collection("users").doc(BOB_UID);
    await bob.collection("search_history").doc("bob_h").set(validHistoryPayload(BOB_UID));
    await bob.collection("saved_translations").doc("bob_s").set(validSavedPayload(BOB_UID));
    await bob.collection("favorite_mangas").doc("bob_m").set(validFavoritePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobRef = aliceDb.collection("users").doc(BOB_UID);
  await assertFails(bobRef.collection("search_history").get());
  await assertFails(bobRef.collection("saved_translations").doc("bob_s").get());
  await assertFails(bobRef.collection("favorite_mangas").doc("bob_m").delete());
  await assertFails(
    bobRef.collection("saved_translations").doc("bob_s").update({
      mastered: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
