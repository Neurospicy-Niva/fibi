rs.initiate()
sleep(5000)
db.createUser(
    {
        user: "fibiapp",
        pwd:  "whydoesitalwaysRAINonme",
        roles: [ { role: "readWrite", db: "fibi" }]
    }
)
db.friends.insertOne({
    name: 'roland',
    loveLanguage: 'programming'
})