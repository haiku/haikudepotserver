# User Rating

A "user rating" is an opinion given by a user of the application-server system about a particular version of a package.  The user rating contains optional details such as;

- A comment
- A rating from zero to five
- A stability value;
  - Will not start up (`nostart`)
  - Very unstable (`veryunstable`)
  - Unstable but usable (`unstablebutusable`)
  - Mostly stable (`mostlystable`)
  - Stable (`stable`)

The user rating is always given in the context of a specific package version even though, from the user's perspective, it may apply to the package as a whole.  This is important to give context to the user rating. It is naturally still possible to view all of the user ratings for all or some package versions of a package.

User ratings' aggregates are considered in the context of a single repository.

A user rating is also associated with a "natural language" such as English, German or Japanese so that the language of the comment (if present) can be know.

## Package Rating Derivation

After some time, a number of users may have submitted user ratings.  After there are sufficient ratings to reach or exceed a sample size minimum, it is possible to derive a rating for the package as a whole --- an aggregate.  If the situation is such that there are an insufficient number of user ratings then the package does not have a rating.  In regard to API responses, this situation will yield a `null` value for a package's rating.  A minimum value can be configured (see [here](config.md)) to define this minimum sample size.

## Finding Ratings for the Derivation

User ratings are only considered for a number of versions back up to the present.  The number of versions back is able to be configured  (see [here](config.md)).  In deciding what constitutes a prior version only the following elements of the version identity are considered;

- major
- minor
- micro

There is also no consideration of architecture in relation to the "number of versions back".

For each user who has submitted a user rating, the user ratings are sorted by;

1. package version -- The ordering semantics are the same as the C++ code in Haiku
1. create timestamp
1. architecture

This provides a total ordering on the user ratings.  Only the last user rating is considered from this user.  All of the users' user ratings are collected in this way to provide a set of user ratings.

Taking an example -- note that this data forms the basis for the main-flow automated testing -- suppose that we have the following table of package versions (major . minor . micro . revision) and user ratings;

|Version|Architecture|User 1|User 2|User 3|User 4|User 5|
|---|---|---|---|---|---|---|
|0.0.9|x86| | | |2|
|1.0.0|x86| |2 ★| | |
|1.0.1|x86| | |1 ★| |
|1.0.1.1|x86|3| | | | |
|1.0.2|x86|4|3 ★| | | |
|1.0.2|x86_64|1 ★| | | | |

In the case of User 1 who has submitted multiple user ratings, the order of input is;

- `1.0.1.1`
- `1.0.2` (`x86`)
- `1.0.2` (`x86_64`)

In this case, with a "versions back" configured as 2, only user ratings that are associated with versions equal to or greater than 1.0.0 will be considered. This is because, according to the algorithm, the prior versions are `1.0.2`, `1.0.1` and `1.0.0` and two back from the current version of `1.0.2` yields `1.0.0`.

The rating values which are shown with the "★" symbol are the set of rating values that will be used to aggregate a rating value for the package.  With the minimum number of ratings configured to 3, a rating could be derived for this package because there are enough samples; it would be 1.75.

## Triggering Derived Rating Calculations

Each time a user rating is persisted, the derived rating for the package is calculated.  This mechanic utilizes the ORM's listener architecture and is driven by the class `UserRatingDerivationTriggerListener`.  In a situation of high-load, the system will prevent excess pressure by queuing the derivations.

## Storage of the Sample Size

The number of user ratings used to derive a given rating for a package is stored as well as the rating
on the `Pkg` entity.