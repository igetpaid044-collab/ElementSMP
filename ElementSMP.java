name: Build Plugin
on:
  push:
    branches: [ 'main' ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'
      - name: Build with Maven
        run: mvn clean package
      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ElementSMP
          path: target/*.jar