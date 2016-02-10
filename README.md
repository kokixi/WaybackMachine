# WaybackMachine

WaybackMachine that retrieves the state of website in the past according to a given date. If there's a version for this data, that version of the website will be shown. If not, a version of the previous nearest date will be returned.

To do this, we assume that data provided by the crawler came in the following way:
```json
[
    {
        url:"www.tripadvisor.com", 
        date:"134624589",
        data:[<checksum>,<Web html>]
    },...
]
```

For this example, we will mock the crawler and hardcode some data to work with.

We'll store the data provided by the crawler in a hash in the following way: 
```java
  HashMap<String, ArrayList<VersionDomain> > domains;
```
Where the key is the domain of the website, and the value is an array with all the version available for that domain.

To retrieve the nearest date from the array, we use a binary search which give us the result in O(logn).

Also, to improve the performance of the wayback machine, we have implemented a cache where we are going to store the last five domain accesed. 

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
