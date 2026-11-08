const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read rabbits", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("rabbits").get());
});

test("Authenticated user: can create and read their own rabbit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const rabbitRef = aliceDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1");

  await assertSucceeds(
    rabbitRef.set({
      userId: ALICE_UID,
      localId: 1,
      name: "Bugs",
      sex: "Male",
      dateOfBirth: Date.now(),
      breed: "Fauve de Bourgogne",
      cageNumber: "C-12",
      weight: 3.5,
      notes: "En bonne santé",
      isActive: true,
      status: "Actif",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(rabbitRef.get());
});

test("Cross-user isolation: Bob cannot read Alice's rabbits", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1").set({
      userId: ALICE_UID,
      name: "Secret Rabbit",
      sex: "Female",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("rabbits").get());
});

test("Schema validation: rejects rabbit with missing name or invalid type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const rabbitRef = aliceDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("bad_rabbit");

  // Missing name
  await assertFails(
    rabbitRef.set({
      userId: ALICE_UID,
      sex: "Male",
      createdAt: new Date(),
    })
  );

  // Invalid sex (must be string)
  await assertFails(
    rabbitRef.set({
      userId: ALICE_UID,
      name: "Test",
      sex: 12345,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can manage mating records, inventory and reminders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Mating record
  const matingRef = aliceDb.collection("users").doc(ALICE_UID).collection("mating_records").doc("mating_1");
  await assertSucceeds(
    matingRef.set({
      userId: ALICE_UID,
      maleId: 1,
      femaleId: 2,
      matingDate: Date.now(),
      expectedBirthDate: Date.now() + 30 * 86400000,
      status: "Accouplé",
      notes: "Saillie réussie",
      createdAt: new Date(),
    })
  );

  // Inventory item
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("inventory_items").doc("item_1");
  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      name: "Granulés élevage",
      category: "Alimentation",
      quantity: 50.0,
      unit: "kg",
      lowStockThreshold: 10.0,
      notes: "Sac de 25kg",
      createdAt: new Date(),
    })
  );

  // Reminder
  const reminderRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1");
  await assertSucceeds(
    reminderRef.set({
      userId: ALICE_UID,
      title: "Palpation",
      dueDate: Date.now() + 12 * 86400000,
      rabbitId: 2,
      category: "Palpation",
      isCompleted: false,
      createdAt: new Date(),
    })
  );
});

/* M22 : la collection web reelle (users/{uid}/cheptel) et le profil n'etaient
   couverts par aucun test — seuls les schemas Android l'etaient. */
test("Cheptel (web): owner can write a valid doc, invalid docs are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cheptel = aliceDb.collection("users").doc(ALICE_UID).collection("cheptel");
  const good = { kind: "rabbit", id: "abc123", name: "Bella", updatedAt: new Date().toISOString() };

  await assertSucceeds(cheptel.doc("rabbit-abc123").set(good));
  // docId non prefixe par le kind
  await assertFails(cheptel.doc("mating-abc123").set(good));
  // updatedAt manquant (M21 : requis)
  const noTs = { ...good };
  delete noTs.updatedAt;
  await assertFails(cheptel.doc("rabbit-def456").set(noTs));
  // updatedAt non-chaine
  await assertFails(cheptel.doc("rabbit-ghi789").set({ ...good, id: "ghi789", updatedAt: 123 }));
  // kind inconnu
  await assertFails(cheptel.doc("rabbit-jkl012").set({ ...good, id: "jkl012", kind: "inconnu" }));
  // un autre utilisateur ne peut ni lire ni ecrire
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cheptel").doc("rabbit-abc123").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cheptel").doc("rabbit-zz9999").set({ ...good, id: "zz9999" }));
});

test("Profil: owner can write their profile, userId mismatch is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set({ userId: ALICE_UID, email: "alice@example.com", farmName: "Ferme Alice", updatedAt: new Date() }));
  await assertFails(profileRef.set({ userId: BOB_UID, farmName: "Usurpation" }));
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set({ userId: BOB_UID }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});
