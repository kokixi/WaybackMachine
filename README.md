# WaybackMachine

Mock of a Wayback Machine that retrieves the state of a website in a given date. If there's a version for that exact date, it would be displayed. Otherwise, the closest version prior to that time will be shown.

## How it works

We assume data provided by the crawler comes in the following format:
```json
[
    {
        url:"www.tripadvisor.com", 
        date:"2006-01-01",
        data:[<checksum>,<Web html>]
    },...
]
```

In this project, I am mocking the crawler and hardcoding some pages to work with.

Data provided by the crawler is cached in the following structure:
```
{
  urlMD5 : [
    {
        date,
        checksum
    },
    ...
  ] 
}
```
Date are sorted by ascending order to facilitate the search as mentioned below.

The structure in java looks like this:

```java
HashMap<String, ArrayList<UrlVersion> > urls;
```
where the key is the url of the website, and the value is an array with all the versions available for that url.

### Performance considerations

To retrieve the nearest date from the array, we use a **binary search which give us the result in O(logn).**

Also, to improve the performance of the wayback machine, I have implemented a very rudimentary cache where the latest five urls are kept in memory for fastest access. This is a round robin list where the least accessed url is deleted on behalf of the newly accessed one.

## How to run it
Compile: 

```java
  javac WaybackMachine.java
```
Run:
```java
  java WaybackMachine
```

Examples:

www.google.com              2009-02-04
    
www.tripadvisor.com         2006-01-06
    
www.tripadvisor.com         2008-08-31

www.tripadvisor.com         2009-03-08

www.tripadvisor.com         2012-02-02
